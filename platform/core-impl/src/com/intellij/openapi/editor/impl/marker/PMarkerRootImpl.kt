// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.impl.DocumentOpMarkerEdit
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.util.TextRange
import com.intellij.util.Processor
import com.intellij.util.containers.ConcurrentLongObjectMap
import com.intellij.util.containers.Java11Shim
import org.jetbrains.annotations.TestOnly
import java.lang.ref.WeakReference

/**
 * Immutable persistent marker root backed by an AVL tree keyed by `(startOffset, markerId)`.
 *
 * Marker IDs are stable logical node identities. [states] is a persistent radix map, so different roots can store
 * different node contents for the same marker ID. Each valid node stores its own parent ID, which allows a marker to be
 * resolved by ID even when an ancestor subtree carries an unpushed lazy offset delta.
 *
 * Equal start offsets are supported. If an edit collapses differently ordered starts to one offset and therefore
 * violates marker-ID tie ordering, the affected middle part is sorted before it is rebuilt as a balanced AVL tree.
 */
class PMarkerRootImpl private constructor(
  private val rootId: Long,
  private val states: PersistentLongMap<StoredNode>,
) : PMarkerRoot {
  private val cachedDelta: ConcurrentLongObjectMap<Int> = Java11Shim.createConcurrentLongObjectMap()

  override fun resolve(markerId: Long, absentRange: TextRange): PMarkerResolution {
    return when (val state = states.getUnchecked(markerId)) {
      null -> PMarkerResolution.Absent(absentRange.startOffset, absentRange.endOffset)
      is AbsentNode -> PMarkerResolution.Absent(state.startOffset, state.endOffset)
      is InvalidNode -> PMarkerResolution.Invalid(
        state.reason,
        state.startOffset,
        state.endOffset,
      )
      is ValidNode -> {
        val ancestorDelta = ancestorDelta(state, markerId)
        PMarkerResolution.Valid(state.entry.startOffset + ancestorDelta, state.entry.endOffset + ancestorDelta)
      }
    }
  }

  override fun insert(
    markerId: Long,
    startOffset: Int,
    endOffset: Int,
    spec: MarkerSpec,
    flavorFlags: Byte,
    markerReference: WeakReference<SnapshotRangeMarkerImpl>?,
  ): PMarkerRoot {
    require(startOffset >= 0) { "startOffset must be non-negative" }
    require(endOffset >= startOffset) { "endOffset must not precede startOffset" }
    val existingState = states.getUnchecked(markerId)
    require(existingState == null || existingState is AbsentNode) { "Marker $markerId already exists" }

    val editor = Editor(states)
    editor.putValid(
      markerId,
      ValidNode(
        MarkerEntry(markerId, startOffset, endOffset, spec, flavorFlags, markerReference),
        parentId = NULL_NODE,
        leftId = NULL_NODE,
        rightId = NULL_NODE,
        height = 1,
        maximumEndOffset = endOffset,
        lazyOffsetDelta = 0,
        subtreeFlavorFlags = flavorFlags,
      )
    )

    val newRoot = insertAvl(editor, rootId, markerId)
    editor.setParent(newRoot, NULL_NODE)
    return PMarkerRootImpl(newRoot, editor.build())
  }

  override fun updateFlavor(markerId: Long, flavorFlags: Byte): PMarkerRoot {
    val state = states.getUnchecked(markerId) as? ValidNode ?: return this
    if (state.entry.flavorFlags == flavorFlags) return this

    val editor = Editor(states)
    editor.putValid(markerId, state.copy(entry = state.entry.copy(flavorFlags = flavorFlags)))
    var currentId = markerId
    while (currentId != NULL_NODE) {
      val node = editor.valid(currentId)
      val updatedFlavorFlags = subtreeFlavorFlags(editor, node.entry, node.leftId, node.rightId)
      if (updatedFlavorFlags != node.subtreeFlavorFlags) {
        editor.putValid(currentId, node.copy(subtreeFlavorFlags = updatedFlavorFlags))
      }
      currentId = node.parentId
    }
    return PMarkerRootImpl(rootId, editor.build())
  }

  override fun updateSpec(markerId: Long, spec: MarkerSpec): PMarkerRoot {
    val state = states.getUnchecked(markerId) as? ValidNode ?: return this
    return PMarkerRootImpl(
      rootId,
      states.put(markerId, state.copy(entry = state.entry.copy(spec = spec))),
    )
  }

  override fun markerReference(markerId: Long): WeakReference<SnapshotRangeMarkerImpl>? {
    return when (val state = states.getUnchecked(markerId)) {
      null -> null
      is AbsentNode -> state.markerReference
      is InvalidNode -> state.markerReference
      is ValidNode -> state.entry.markerReference
    }
  }

  override fun remove(markerId: Long): PMarkerRoot {
    return when (val state = states.getUnchecked(markerId)) {
      null -> this
      is AbsentNode -> this
      is InvalidNode -> PMarkerRootImpl(
        rootId,
        states.put(markerId, AbsentNode(state.startOffset, state.endOffset, state.markerReference))
      )
      is ValidNode -> {
        val offsetDelta = ancestorDelta(state, markerId)
        val startOffset = state.entry.startOffset + offsetDelta
        val endOffset = state.entry.endOffset + offsetDelta
        val key = PositionKey(startOffset, markerId)
        val editor = Editor(states)
        val newRoot = removeByKey(editor, rootId, key)
        editor.putAbsent(markerId, startOffset, endOffset, state.entry.markerReference)
        editor.setParent(newRoot, NULL_NODE)
        PMarkerRootImpl(newRoot, editor.build())
      }
    }
  }

  override fun purge(markerId: Long): PMarkerRoot {
    return when (val state = states.getUnchecked(markerId)) {
      null -> this
      is AbsentNode, is InvalidNode -> PMarkerRootImpl(rootId, states.remove(markerId))
      is ValidNode -> {
        val startOffset = state.entry.startOffset + ancestorDelta(state, markerId)
        val editor = Editor(states)
        val newRoot = removeByKey(editor, rootId, PositionKey(startOffset, markerId))
        editor.remove(markerId)
        editor.setParent(newRoot, NULL_NODE)
        PMarkerRootImpl(newRoot, editor.build())
      }
    }
  }

  @TestOnly
  fun containsMarkerId(markerId: Long): Boolean = states.getUnchecked(markerId) != null

  override fun applyEdit(op: DocumentOp): PMarkerRoot {
    val edit = textEdit(op) ?: return this
    validateEdit(edit)
    val editStart = edit.startOffset
    val editEnd = edit.endOffset
    val oldLength = editEnd - editStart
    val newLength = edit.newLength
    if (oldLength == 0 && newLength == 0 || rootId == NULL_NODE) return this

    val delta = newLength - oldLength
    val editor = Editor(states)

    val (before, fromEditStart) = splitByStart(editor, rootId, editStart, equalGoesLeft = false)
    val (middle, after) = splitByStart(editor, fromEditStart, editEnd, equalGoesLeft = true)

    val updatedBefore = updateMarkersStartingBeforeEdit(editor, before, edit)
    val middleEntries = ArrayList<MarkerEntry>()
    collectEntries(editor, middle, 0, middleEntries)

    val transformedMiddle = ArrayList<MarkerEntry>(middleEntries.size)
    for (entry in middleEntries) {
      when (val update = transform(entry, edit)) {
        is TransformResult.Valid -> transformedMiddle.add(update.entry)
        is TransformResult.Invalid -> editor.putInvalid(entry, update.reason)
      }
    }

    if (!isSortedByPosition(transformedMiddle)) transformedMiddle.sortWith(ENTRY_COMPARATOR)

    val rebuiltMiddle = buildBalanced(editor, transformedMiddle)
    val shiftedAfter = shift(editor, after, delta)

    var newRoot = joinDisjoint(editor, updatedBefore, rebuiltMiddle)
    newRoot = joinDisjoint(editor, newRoot, shiftedAfter)
    if (oldLength == 0 && edit.moveOffset != editStart) {
      // The regular insertion update has already shifted the source range. Retarget its contained markers to the
      // inserted copy before the deletion half of moveText is applied.
      val moveStart = edit.moveOffset
      val moveEnd = moveStart + newLength
      newRoot = retargetContainedMarkers(editor, newRoot, moveStart, moveEnd, editStart - moveStart)
    }
    editor.setParent(newRoot, NULL_NODE)
    return PMarkerRootImpl(newRoot, editor.build())
  }

  /**
   * Reports IDs of valid markers intersecting the half-open range `[startOffset, endOffset)`.
   *
   * This implementation uses a start-ordered tree augmented with subtree maximum ends. It is output-sensitive in
   * typical cases, but unlike a priority-search tree it does not guarantee worst-case `O(log n + k)` reporting.
   */
  override fun processRangeMarkersOverlappingWith(
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
    processor: Processor<in MarkerEntry>,
  ): Boolean {
    require(startOffset >= 0) { "startOffset must be non-negative" }
    require(endOffset >= startOffset) { "endOffset must not precede startOffset" }
    return processRangeMarkersOverlappingWith(
      rootId,
      ancestorDelta = 0,
      queryStart = startOffset,
      queryEnd = endOffset,
      requiredFlavorFlags = tastePreference and ALL_FLAVOR_FLAGS,
      processor = processor,
    )
  }

  private fun ancestorDelta(state: ValidNode, markerId: Long): Int {
    return cachedDelta.computeIfAbsent(markerId) {
      var result = 0
      var parentId = state.parentId

      while (parentId != NULL_NODE) {
        val parent = states.getUnchecked(parentId) as? ValidNode
                     ?: throw IllegalStateException("Parent $parentId is not a valid marker node")
        result += parent.lazyOffsetDelta
        parentId = parent.parentId
      }
      result
    }
  }

  private fun processRangeMarkersOverlappingWith(
    nodeId: Long,
    ancestorDelta: Int,
    queryStart: Int,
    queryEnd: Int,
    requiredFlavorFlags: Int,
    processor: Processor<in MarkerEntry>,
  ): Boolean {
    if (nodeId == NULL_NODE) {
      return true
    }
    val node = states.getUnchecked(nodeId) as ValidNode
    if (!containsAllFlavorFlags(node.subtreeFlavorFlags, requiredFlavorFlags)) {
      return true
    }
    if (node.maximumEndOffset + ancestorDelta <= queryStart) {
      return true
    }

    val childDelta = ancestorDelta + node.lazyOffsetDelta
    if (!processRangeMarkersOverlappingWith(
        node.leftId,
        childDelta,
        queryStart,
        queryEnd,
        requiredFlavorFlags,
        processor,
      )) {
      return false
    }

    val start = node.entry.startOffset + ancestorDelta
    val end = node.entry.endOffset + ancestorDelta
    if (start < queryEnd && end > queryStart && containsAllFlavorFlags(node.entry.flavorFlags, requiredFlavorFlags)) {
      if (!processor.process(node.entry)) {
        return false
      }
    }
    if (start < queryEnd) {
      if (!processRangeMarkersOverlappingWith(
          node.rightId,
          childDelta,
          queryStart,
          queryEnd,
          requiredFlavorFlags,
          processor,
        )) {
        return false
      }
    }
    return true
  }

  private sealed interface StoredNode

  private data class ValidNode(
    val entry: MarkerEntry,
    val parentId: Long,
    val leftId: Long,
    val rightId: Long,
    val height: Int,
    val maximumEndOffset: Int,
    val lazyOffsetDelta: Int,
    val subtreeFlavorFlags: Byte,
  ) : StoredNode

  private data class InvalidNode(
    val reason: String,
    val startOffset: Int,
    val endOffset: Int,
    val markerReference: WeakReference<SnapshotRangeMarkerImpl>?,
  ) : StoredNode

  private data class AbsentNode(
    val startOffset: Int,
    val endOffset: Int,
    val markerReference: WeakReference<SnapshotRangeMarkerImpl>?,
  ) : StoredNode

  private data class PositionKey(val startOffset: Int, val markerId: Long) : Comparable<PositionKey> {
    constructor(entry: MarkerEntry) : this(entry.startOffset, entry.markerId)

    override fun compareTo(other: PositionKey): Int {
      val byOffset = startOffset.compareTo(other.startOffset)
      return if (byOffset != 0) byOffset else markerId.compareTo(other.markerId)
    }
  }

  private sealed class TransformResult {
    data class Valid(val entry: MarkerEntry) : TransformResult()
    data class Invalid(val reason: String) : TransformResult()
  }

  private data class TextEdit(
    val startOffset: Int,
    val endOffset: Int,
    val newLength: Int,
    val originStartOffset: Int,
    val originEndOffset: Int,
    val moveOffset: Int,
  )

  private data class ExtractMinimumResult(val rootId: Long, val minimumId: Long)

  private class Editor(states: PersistentLongMap<StoredNode>) {
    private val builder = states.builder()

    fun valid(markerId: Long): ValidNode = builder.getUnchecked(markerId) as? ValidNode
                                           ?: throw IllegalStateException("Marker $markerId is not a valid tree node")

    fun putValid(markerId: Long, node: ValidNode) {
      builder.put(markerId, node)
    }

    fun putInvalid(entry: MarkerEntry, reason: String) {
      builder.put(
        entry.markerId,
        InvalidNode(reason, entry.startOffset, entry.endOffset, entry.markerReference)
      )
    }

    fun putAbsent(
      markerId: Long,
      startOffset: Int,
      endOffset: Int,
      markerReference: WeakReference<SnapshotRangeMarkerImpl>?,
    ) {
      builder.put(markerId, AbsentNode(startOffset, endOffset, markerReference))
    }

    fun remove(markerId: Long) {
      builder.remove(markerId)
    }

    fun setParent(markerId: Long, parentId: Long) {
      if (markerId == NULL_NODE) return
      val node = valid(markerId)
      if (node.parentId != parentId) putValid(markerId, node.copy(parentId = parentId))
    }

    fun build(): PersistentLongMap<StoredNode> = builder.build()
  }

  companion object {
    private const val INVALIDATED_BY_EDIT = "Marker was invalidated by a document edit"
    private const val ALL_FLAVOR_FLAGS = 0xFF

    private val ENTRY_COMPARATOR = Comparator<MarkerEntry> { first, second -> PositionKey(first).compareTo(PositionKey(second)) }
    private const val NULL_NODE: Long = 0
    private val EMPTY = PMarkerRootImpl(NULL_NODE, PersistentLongMap.empty(PersistentLongMapImplementation.VECTOR_64))


    fun empty(): PMarkerRootImpl = EMPTY

    private fun textEdit(op: DocumentOp): TextEdit? {
      if (op is DocumentOpMarkerEdit) {
        return op.markerEdit?.let {
          TextEdit(
            startOffset = it.startOffset,
            endOffset = it.endOffset,
            newLength = it.newLength,
            originStartOffset = it.originStartOffset,
            originEndOffset = it.originEndOffset,
            moveOffset = it.moveOffset,
          )
        }
      }
      return when (op) {
        is DocumentOp.Insert -> {
          val offset = op.offset()
          TextEdit(
            startOffset = offset,
            endOffset = offset,
            newLength = op.fragment().length,
            originStartOffset = offset,
            originEndOffset = offset,
            moveOffset = offset,
          )
        }
        is DocumentOp.Delete -> {
          val offset = op.offset()
          val length = op.length()
          require(offset >= 0) { "DocumentOp.Delete offset must be non-negative" }
          require(length >= 0) { "DocumentOp.Delete length must be non-negative" }
          require(offset <= Int.MAX_VALUE - length) { "DocumentOp.Delete range overflows Int" }
          TextEdit(
            startOffset = offset,
            endOffset = offset + length,
            newLength = 0,
            originStartOffset = offset,
            originEndOffset = offset + length,
            moveOffset = offset,
          )
        }
        else -> null
      }
    }

    private fun validateEdit(edit: TextEdit) {
      require(edit.startOffset >= 0) { "Document edit startOffset must be non-negative" }
      require(edit.endOffset >= edit.startOffset) { "Document edit endOffset must not precede startOffset" }
      require(edit.startOffset <= Int.MAX_VALUE - edit.newLength) { "Document edit new range overflows Int" }
      require(edit.moveOffset >= 0) { "Document edit moveOffset must be non-negative" }
      require(edit.moveOffset <= Int.MAX_VALUE - edit.newLength) { "Document edit move range overflows Int" }
    }

    private fun key(markerId: Long, node: ValidNode): PositionKey = PositionKey(node.entry.startOffset, markerId)

    private fun height(editor: Editor, markerId: Long): Int = if (markerId != NULL_NODE) editor.valid(markerId).height else 0

    private fun containsAllFlavorFlags(flavorFlags: Byte, requiredFlavorFlags: Int): Boolean =
      (flavorFlags.toInt() and requiredFlavorFlags) == requiredFlavorFlags

    private fun subtreeFlavorFlags(editor: Editor, markerId: Long): Int =
      if (markerId == NULL_NODE) 0 else editor.valid(markerId).subtreeFlavorFlags.toInt()

    private fun subtreeFlavorFlags(editor: Editor, entry: MarkerEntry, leftId: Long, rightId: Long): Byte =
      (entry.flavorFlags.toInt() or subtreeFlavorFlags(editor, leftId) or subtreeFlavorFlags(editor, rightId)).toByte()

    private fun balanceFactor(editor: Editor, node: ValidNode): Int {
      return height(editor, node.leftId) - height(editor, node.rightId)
    }

    private fun shift(editor: Editor, nodeId: Long, delta: Int): Long {
      if (nodeId == NULL_NODE || delta == 0) return nodeId
      val node = editor.valid(nodeId)
      editor.putValid(
        nodeId,
        node.copy(
          entry = node.entry.copy(
            startOffset = node.entry.startOffset + delta,
            endOffset = node.entry.endOffset + delta,
          ),
          maximumEndOffset = node.maximumEndOffset + delta,
          lazyOffsetDelta = node.lazyOffsetDelta + delta
        )
      )
      return nodeId
    }

    private fun push(editor: Editor, nodeId: Long): ValidNode {
      val node = editor.valid(nodeId)
      val delta = node.lazyOffsetDelta
      if (delta == 0) return node

      shift(editor, node.leftId, delta)
      shift(editor, node.rightId, delta)
      val updated = node.copy(lazyOffsetDelta = 0)
      editor.putValid(nodeId, updated)
      return updated
    }

    private fun rewrite(
      editor: Editor,
      markerId: Long,
      node: ValidNode,
      parentId: Long,
      leftId: Long,
      rightId: Long,
      entry: MarkerEntry = node.entry,
    ): ValidNode {
      check(node.lazyOffsetDelta == 0) { "Node $markerId must be pushed before it is rewritten" }
      val updated = node.copy(
        entry = entry,
        parentId = parentId,
        leftId = leftId,
        rightId = rightId,
        height = maxOf(height(editor, leftId), height(editor, rightId)) + 1,
        maximumEndOffset = maxOf(
          entry.endOffset,
          if (leftId != NULL_NODE) editor.valid(leftId).maximumEndOffset else Int.MIN_VALUE,
          if (rightId != NULL_NODE) editor.valid(rightId).maximumEndOffset else Int.MIN_VALUE
        ),
        lazyOffsetDelta = 0,
        subtreeFlavorFlags = subtreeFlavorFlags(editor, entry, leftId, rightId),
      )
      if (updated != node) editor.putValid(markerId, updated)
      editor.setParent(leftId, markerId)
      editor.setParent(rightId, markerId)
      return updated
    }

    private fun detachAsLeaf(editor: Editor, markerId: Long): ValidNode {
      val node = push(editor, markerId)
      editor.setParent(node.leftId, NULL_NODE)
      editor.setParent(node.rightId, NULL_NODE)
      return rewrite(editor, markerId, node, NULL_NODE, NULL_NODE, NULL_NODE)
    }

    private fun rotateLeft(editor: Editor, rootId: Long): Long {
      val root = push(editor, rootId)
      val rightId = checkNotNull(root.rightId) { "Cannot rotate node $rootId left without a right child" }
      val right = push(editor, rightId)
      val parentId = root.parentId
      val middleId = right.leftId

      rewrite(editor, rootId, root, rightId, root.leftId, middleId)
      rewrite(editor, rightId, right, parentId, rootId, right.rightId)
      return rightId
    }

    private fun rotateRight(editor: Editor, rootId: Long): Long {
      val root = push(editor, rootId)
      val leftId = checkNotNull(root.leftId) { "Cannot rotate node $rootId right without a left child" }
      val left = push(editor, leftId)
      val parentId = root.parentId
      val middleId = left.rightId

      rewrite(editor, rootId, root, leftId, middleId, root.rightId)
      rewrite(editor, leftId, left, parentId, left.leftId, rootId)
      return leftId
    }

    private fun rebalance(editor: Editor, rootId: Long): Long {
      var root = push(editor, rootId)
      val factor = balanceFactor(editor, root)

      if (factor > 1) {
        val leftId = checkNotNull(root.leftId)
        val left = push(editor, leftId)
        if (balanceFactor(editor, left) < 0) {
          val newLeft = rotateLeft(editor, leftId)
          root = push(editor, rootId)
          rewrite(editor, rootId, root, root.parentId, newLeft, root.rightId)
        }
        return rotateRight(editor, rootId)
      }

      if (factor < -1) {
        val rightId = checkNotNull(root.rightId)
        val right = push(editor, rightId)
        if (balanceFactor(editor, right) > 0) {
          val newRight = rotateRight(editor, rightId)
          root = push(editor, rootId)
          rewrite(editor, rootId, root, root.parentId, root.leftId, newRight)
        }
        return rotateLeft(editor, rootId)
      }

      return rootId
    }

    private fun insertAvl(editor: Editor, rootId: Long, markerId: Long): Long {
      if (rootId == NULL_NODE) return markerId

      val root = push(editor, rootId)
      val inserted = editor.valid(markerId)
      if (key(markerId, inserted) < key(rootId, root)) {
        val newLeft = insertAvl(editor, root.leftId, markerId)
        rewrite(editor, rootId, root, root.parentId, newLeft, root.rightId)
      }
      else {
        val newRight = insertAvl(editor, root.rightId, markerId)
        rewrite(editor, rootId, root, root.parentId, root.leftId, newRight)
      }
      return rebalance(editor, rootId)
    }

    private fun removeByKey(editor: Editor, rootId: Long, target: PositionKey): Long {
      if (rootId == NULL_NODE) return NULL_NODE
      val root = push(editor, rootId)
      val comparison = target.compareTo(key(rootId, root))

      if (comparison < 0) {
        val newLeft = removeByKey(editor, root.leftId, target)
        rewrite(editor, rootId, root, root.parentId, newLeft, root.rightId)
        return rebalance(editor, rootId)
      }

      if (comparison > 0) {
        val newRight = removeByKey(editor, root.rightId, target)
        rewrite(editor, rootId, root, root.parentId, root.leftId, newRight)
        return rebalance(editor, rootId)
      }

      val parentId = root.parentId
      if (root.leftId == NULL_NODE) {
        editor.setParent(root.rightId, parentId)
        return root.rightId
      }
      if (root.rightId == NULL_NODE) {
        editor.setParent(root.leftId, parentId)
        return root.leftId
      }

      val extracted = extractMinimum(editor, root.rightId)
      val successor = push(editor, extracted.minimumId)
      rewrite(editor, extracted.minimumId, successor, parentId, root.leftId, extracted.rootId)
      return rebalance(editor, extracted.minimumId)
    }

    private fun extractMinimum(editor: Editor, rootId: Long): ExtractMinimumResult {
      val root = push(editor, rootId)
      if (root.leftId == NULL_NODE) {
        val remainingRoot = root.rightId
        editor.setParent(remainingRoot, root.parentId)
        rewrite(editor, rootId, root, NULL_NODE, NULL_NODE, NULL_NODE)
        return ExtractMinimumResult(remainingRoot, rootId)
      }

      val extracted = extractMinimum(editor, root.leftId)
      rewrite(editor, rootId, root, root.parentId, extracted.rootId, root.rightId)
      return ExtractMinimumResult(rebalance(editor, rootId), extracted.minimumId)
    }

    private fun joinWithPivot(editor: Editor, leftId: Long, pivotId: Long, rightId: Long): Long {
      editor.setParent(leftId, NULL_NODE)
      editor.setParent(rightId, NULL_NODE)
      detachAsLeaf(editor, pivotId)
      return joinPrepared(editor, leftId, pivotId, rightId)
    }

    private fun checkNotNull(id: Long): Long {
      check(id != NULL_NODE)
      return id
    }

    private fun joinPrepared(editor: Editor, leftId: Long, pivotId: Long, rightId: Long): Long {
      val leftHeight = height(editor, leftId)
      val rightHeight = height(editor, rightId)

      if (leftHeight <= rightHeight + 1 && rightHeight <= leftHeight + 1) {
        val pivot = push(editor, pivotId)
        rewrite(editor, pivotId, pivot, NULL_NODE, leftId, rightId)
        return pivotId
      }

      if (leftHeight > rightHeight + 1) {
        val leftRootId = checkNotNull(leftId)
        val leftRoot = push(editor, leftRootId)
        val detachedRight = leftRoot.rightId
        editor.setParent(detachedRight, NULL_NODE)
        val joinedRight = joinPrepared(editor, detachedRight, pivotId, rightId)
        rewrite(editor, leftRootId, leftRoot, NULL_NODE, leftRoot.leftId, joinedRight)
        val result = rebalance(editor, leftRootId)
        editor.setParent(result, NULL_NODE)
        return result
      }

      val rightRootId = checkNotNull(rightId)
      val rightRoot = push(editor, rightRootId)
      val detachedLeft = rightRoot.leftId
      editor.setParent(detachedLeft, NULL_NODE)
      val joinedLeft = joinPrepared(editor, leftId, pivotId, detachedLeft)
      rewrite(editor, rightRootId, rightRoot, NULL_NODE, joinedLeft, rightRoot.rightId)
      val result = rebalance(editor, rightRootId)
      editor.setParent(result, NULL_NODE)
      return result
    }

    private fun joinDisjoint(editor: Editor, leftId: Long, rightId: Long): Long {
      if (leftId == NULL_NODE) {
        editor.setParent(rightId, NULL_NODE)
        return rightId
      }
      if (rightId == NULL_NODE) {
        editor.setParent(leftId, NULL_NODE)
        return leftId
      }

      editor.setParent(leftId, NULL_NODE)
      editor.setParent(rightId, NULL_NODE)
      val extracted = extractMinimum(editor, rightId)
      val result = joinPrepared(editor, leftId, extracted.minimumId, extracted.rootId)
      editor.setParent(result, NULL_NODE)
      return result
    }

    private fun splitByStart(
      editor: Editor,
      rootId: Long,
      boundaryOffset: Int,
      equalGoesLeft: Boolean,
    ): Pair<Long, Long> {
      if (rootId == NULL_NODE) return NULL_NODE to NULL_NODE

      val root = push(editor, rootId)
      val leftId = root.leftId
      val rightId = root.rightId
      val goesLeft = root.entry.startOffset < boundaryOffset || equalGoesLeft && root.entry.startOffset == boundaryOffset
      detachAsLeaf(editor, rootId)

      return if (goesLeft) {
        val (middle, greater) = splitByStart(editor, rightId, boundaryOffset, equalGoesLeft)
        val lessOrEqual = joinPrepared(editor, leftId, rootId, middle)
        editor.setParent(lessOrEqual, NULL_NODE)
        editor.setParent(greater, NULL_NODE)
        lessOrEqual to greater
      }
      else {
        val (less, middle) = splitByStart(editor, leftId, boundaryOffset, equalGoesLeft)
        val greaterOrEqual = joinPrepared(editor, middle, rootId, rightId)
        editor.setParent(less, NULL_NODE)
        editor.setParent(greaterOrEqual, NULL_NODE)
        less to greaterOrEqual
      }
    }

    private fun updateMarkersStartingBeforeEdit(editor: Editor, rootId: Long, edit: TextEdit): Long {
      if (rootId == NULL_NODE) return NULL_NODE
      val initial = editor.valid(rootId)
      if (initial.maximumEndOffset < edit.startOffset) return rootId

      val root = push(editor, rootId)
      val leftId = root.leftId
      val rightId = root.rightId
      val entry = root.entry
      detachAsLeaf(editor, rootId)

      val newLeft = updateMarkersStartingBeforeEdit(editor, leftId, edit)
      val newRight = updateMarkersStartingBeforeEdit(editor, rightId, edit)

      return when (val update = transform(entry, edit)) {
        is TransformResult.Valid -> {
          check(update.entry.startOffset == entry.startOffset) {
            "An edit changed the start of a marker that starts before the edit"
          }
          val leaf = editor.valid(rootId)
          rewrite(editor, rootId, leaf, NULL_NODE, NULL_NODE, NULL_NODE, update.entry)
          val result = joinPrepared(editor, newLeft, rootId, newRight)
          editor.setParent(result, NULL_NODE)
          result
        }
        is TransformResult.Invalid -> {
          editor.putInvalid(entry, update.reason)
          val result = joinDisjoint(editor, newLeft, newRight)
          editor.setParent(result, NULL_NODE)
          result
        }
      }
    }

    private fun collectEntries(
      editor: Editor,
      rootId: Long,
      ancestorDelta: Int,
      destination: MutableList<MarkerEntry>,
    ) {
      if (rootId == NULL_NODE) return
      val node = editor.valid(rootId)
      val childDelta = ancestorDelta + node.lazyOffsetDelta
      collectEntries(editor, node.leftId, childDelta, destination)
      destination.add(node.entry.copy(
          startOffset = node.entry.startOffset + ancestorDelta,
          endOffset = node.entry.endOffset + ancestorDelta,
        )
      )
      collectEntries(editor, node.rightId, childDelta, destination)
    }

    private fun retargetContainedMarkers(
      editor: Editor,
      rootId: Long,
      moveStart: Int,
      moveEnd: Int,
      offsetDelta: Int,
    ): Long {
      val affected = ArrayList<MarkerEntry>()
      collectContainedEntries(editor, rootId, 0, moveStart, moveEnd, affected)

      var result = rootId
      for (entry in affected) {
        result = removeByKey(editor, result, PositionKey(entry))
        val retargeted = entry.copy(
          startOffset = entry.startOffset + offsetDelta,
          endOffset = entry.endOffset + offsetDelta,
        )
        editor.putValid(
          entry.markerId,
          ValidNode(
            entry = retargeted,
            parentId = NULL_NODE,
            leftId = NULL_NODE,
            rightId = NULL_NODE,
            height = 1,
            maximumEndOffset = retargeted.endOffset,
            lazyOffsetDelta = 0,
            subtreeFlavorFlags = retargeted.flavorFlags,
          )
        )
        result = insertAvl(editor, result, entry.markerId)
        editor.setParent(result, NULL_NODE)
      }
      return result
    }

    private fun collectContainedEntries(
      editor: Editor,
      rootId: Long,
      ancestorDelta: Int,
      startOffset: Int,
      endOffset: Int,
      destination: MutableList<MarkerEntry>,
    ) {
      if (rootId == NULL_NODE) return
      val node = editor.valid(rootId)
      val nodeStart = node.entry.startOffset + ancestorDelta
      val childDelta = ancestorDelta + node.lazyOffsetDelta

      if (nodeStart >= startOffset) {
        collectContainedEntries(editor, node.leftId, childDelta, startOffset, endOffset, destination)
      }
      val nodeEnd = node.entry.endOffset + ancestorDelta
      if (nodeStart >= startOffset && nodeEnd <= endOffset) {
        destination.add(node.entry.copy(startOffset = nodeStart, endOffset = nodeEnd))
      }
      if (nodeStart <= endOffset) {
        collectContainedEntries(editor, node.rightId, childDelta, startOffset, endOffset, destination)
      }
    }

    private fun isSortedByPosition(entries: List<MarkerEntry>): Boolean {
      for (index in 1 until entries.size) {
        if (PositionKey(entries[index - 1]) > PositionKey(entries[index])) return false
      }
      return true
    }

    private fun buildBalanced(editor: Editor, sortedEntries: List<MarkerEntry>): Long {
      return buildBalanced(editor, sortedEntries, 0, sortedEntries.size, NULL_NODE)
    }

    private fun buildBalanced(
      editor: Editor,
      sortedEntries: List<MarkerEntry>,
      fromIndex: Int,
      toIndex: Int,
      parentId: Long,
    ): Long {
      if (fromIndex >= toIndex) return NULL_NODE
      val middleIndex = (fromIndex + toIndex) ushr 1
      val entry = sortedEntries[middleIndex]
      val leftId = buildBalanced(editor, sortedEntries, fromIndex, middleIndex, entry.markerId)
      val rightId = buildBalanced(editor, sortedEntries, middleIndex + 1, toIndex, entry.markerId)
      val height = maxOf(height(editor, leftId), height(editor, rightId)) + 1
      val maximumEndOffset = maxOf(
        entry.endOffset,
        if (leftId != NULL_NODE) editor.valid(leftId).maximumEndOffset else Int.MIN_VALUE,
        if (rightId != NULL_NODE) editor.valid(rightId).maximumEndOffset else Int.MIN_VALUE
      )
      editor.putValid(
        entry.markerId,
        ValidNode(
          entry = entry,
          parentId = parentId,
          leftId = leftId,
          rightId = rightId,
          height = height,
          maximumEndOffset = maximumEndOffset,
          lazyOffsetDelta = 0,
          subtreeFlavorFlags = subtreeFlavorFlags(editor, entry, leftId, rightId),
        )
      )
      return entry.markerId
    }

    private fun transform(entry: MarkerEntry, edit: TextEdit): TransformResult {
      return if (entry.startOffset == entry.endOffset) {
        transformPoint(entry, edit)
      }
      else {
        transformRange(entry, edit)
      }
    }

    private fun transformPoint(entry: MarkerEntry, edit: TextEdit): TransformResult {
      val point = entry.startOffset
      val editStart = edit.startOffset
      val editEnd = edit.endOffset
      val oldLength = editEnd - editStart
      val newLength = edit.newLength

      if (editStart < point && point < editEnd) return TransformResult.Invalid(INVALIDATED_BY_EDIT)

      if (oldLength == 0 && editStart == point && entry.spec.isGreedyToRight) {
        return TransformResult.Valid(entry.copy(endOffset = point + newLength))
      }

      if (oldLength == 0 && editStart == point && entry.spec.isStickingToRight) {
        val shifted = point + newLength
        return TransformResult.Valid(entry.copy(startOffset = shifted, endOffset = shifted))
      }

      if (point > editEnd || point == editEnd && oldLength > 0) {
        val shifted = point + newLength - oldLength
        return TransformResult.Valid(entry.copy(startOffset = shifted, endOffset = shifted))
      }

      return TransformResult.Valid(entry)
    }

    private fun transformRange(entry: MarkerEntry, edit: TextEdit): TransformResult {
      val startOffset = entry.startOffset
      val endOffset = entry.endOffset
      val editStart = edit.startOffset
      val editEnd = edit.endOffset
      val newLength = edit.newLength
      val delta = newLength - (editEnd - editStart)

      if (editStart > endOffset) return TransformResult.Valid(entry)
      if (!entry.spec.isGreedyToRight && endOffset == editStart) {
        if (editStart == editEnd && edit.originStartOffset < editStart) {
          return TransformResult.Valid(entry.copy(endOffset = endOffset + newLength))
        }
        return TransformResult.Valid(entry)
      }
      if (startOffset > editEnd) {
        return TransformResult.Valid(
          entry.copy(startOffset = startOffset + delta, endOffset = endOffset + delta)
        )
      }
      if (!entry.spec.isGreedyToLeft && startOffset == editEnd) {
        if (editStart == editEnd && edit.originEndOffset > editStart) {
          return TransformResult.Valid(entry.copy(endOffset = endOffset + newLength))
        }
        return TransformResult.Valid(
          entry.copy(startOffset = startOffset + delta, endOffset = endOffset + delta)
        )
      }
      if (startOffset <= editStart && endOffset >= editEnd) {
        return TransformResult.Valid(entry.copy(endOffset = endOffset + delta))
      }
      if (startOffset >= editStart && startOffset <= editEnd && endOffset > editEnd) {
        return TransformResult.Valid(
          entry.copy(startOffset = editStart + newLength, endOffset = endOffset + delta)
        )
      }
      if (endOffset <= editEnd && startOffset < editStart) {
        return TransformResult.Valid(entry.copy(endOffset = editStart))
      }
      return TransformResult.Invalid(INVALIDATED_BY_EDIT)
    }
  }

  override fun toString(): String {
    return if (rootId == 0L) "EMPTY" else super.toString()
  }
}
