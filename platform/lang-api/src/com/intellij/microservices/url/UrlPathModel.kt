package com.intellij.microservices.url

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import com.intellij.util.containers.headTailOrNull
import com.intellij.util.text.PlaceholderTextRanges
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.swing.Icon
import kotlin.collections.AbstractList

sealed class Authority {
  data class Exact(val text: String) : Authority()
  open class Placeholder : Authority()
}

data class UrlResolveRequest(val schemeHint: String?, val authorityHint: String?, val path: UrlPath, val method: String? = null)

interface UrlTargetInfo {
  val schemes: List<String>

  val authorities: List<Authority>

  val path: UrlPath

  val icon: Icon
    get() = AllIcons.Nodes.PpWeb

  val isDeprecated: Boolean
    get() = false

  /**
   * A set of upper-cased HTTP method names, supported by this target. Empty Set means that any method is supported.
   */
  val methods: Set<String>
    get() = emptySet()

  /**
   * Location of Url target declaration. Represented by a class name (e.g MyController), or a file name.
   */
  val source: String
    get() = ""

  val documentationPsiElement: PsiElement?
    get() = resolveToPsiElement()

  fun resolveToPsiElement(): PsiElement?

  val queryParameters: Iterable<UrlQueryParameter>
    get() = emptyList()

  val contentTypes: Set<String>
    get() = emptySet()
}

interface UrlQueryParameter {

  val name: String

  val description: String?

  val repeatable: Boolean

  fun resolveToPsiElement(): PsiElement?
}

fun getDeclaredHttpMethods(methodString: String?): Set<String> =
  if (methodString != null) setOf(methodString.uppercase(Locale.getDefault())) else emptySet()

fun compatibleSchemes(aSchemes: Collection<String>, bSchemes: Collection<String>): Boolean =
  aSchemes.isEmpty() || bSchemes.isEmpty()
  || aSchemes.intersect(bSchemes).isNotEmpty()
  || KNOWN_SCHEMES_GROUPS.any { group -> aSchemes.any { it in group } && bSchemes.any { it in group } }

class UrlPath(val segments: List<PathSegment>) {
  sealed class PathSegment {
    class Exact(val value: String) : PathSegment() {
      override fun equals(other: Any?): Boolean = (other as? Exact)?.value == value
      override fun hashCode(): Int = value.hashCode()
      override fun toString(): String = "PathSegment.Exact($value)"
    }

    val valueIfExact: String? get() = (this as? Exact)?.value

    fun isEmpty(): Boolean = valueIfExact?.isEmpty() == true

    class Variable(val variableName: String?, val regex: String? = null) : PathSegment() {
      override fun equals(other: Any?): Boolean = (other as? Variable)?.variableName == variableName
      override fun hashCode(): Int = variableName.hashCode()
      override fun toString(): String = "PathSegment.Variable({$variableName})"
      fun accepts(value: String): Boolean = true //TODO: add pattern matching for variable
    }

    class Composite(val segments: List<PathSegment>) : PathSegment() {
      override fun equals(other: Any?): Boolean = (other as? Composite)?.segments == segments
      override fun hashCode(): Int = segments.hashCode()
      override fun toString(): String = "PathSegment.Composite($segments)"
    }

    object Undefined : PathSegment()
  }

  @NlsSafe
  fun getPresentation(): String = getPresentation(DEFAULT_PATH_VARIABLE_PRESENTATION)

  interface PathSegmentRenderer {
    fun visitExact(exact: PathSegment.Exact): String = exact.value

    fun visitVariable(variable: PathSegment.Variable): String = STAR

    fun visitUndefined(): String = UNKNOWN_URL_PATH_SEGMENT_PRESENTATION

    fun visitComposite(composite: PathSegment.Composite): String = composite.segments.joinToString("") { s -> patternMatch(s) }

    fun patternMatch(segment: PathSegment): String = when (segment) {
      is PathSegment.Exact -> this.visitExact(segment)
      is PathSegment.Variable -> this.visitVariable(segment)
      is PathSegment.Composite -> this.visitComposite(segment)
      PathSegment.Undefined -> this.visitUndefined()
    }
  }

  @NlsSafe
  fun getPresentation(pathVariableRenderer: PathSegmentRenderer): String = this.segments.joinToString("/") { segment ->
    pathVariableRenderer.patternMatch(segment)
  }

  fun toStringWithStars(): String = this.segments.joinToString("/") {
    when (it) {
      is PathSegment.Exact -> it.value
      is PathSegment.Variable -> STAR
      is PathSegment.Composite -> STAR
      PathSegment.Undefined -> UNKNOWN
    }
  }

  override fun toString(): String = "UrlPath(${toStringWithStars()})"

  fun canBePrefixFor(another: UrlPath): Boolean = commonLength(another) == this.segments.size

  fun commonLength(another: UrlPath): Int {

    tailrec fun commonLength(cur: List<PathSegment>, start: Int, accum: Int): Int {
      val (head, tail) = cur.headTailOrNull() ?: return accum
      val remaining = another.segments.subList(start, another.segments.size)
      when (head) {
        is PathSegment.Exact -> {
          val anotherExactIndex = remaining.indexOfFirst { it is PathSegment.Exact }.takeIf { it != -1 } ?: return accum
          val anotherExact = remaining[anotherExactIndex] as PathSegment.Exact
          return when {
            anotherExact == head ->
              commonLength(tail, start + anotherExactIndex + 1, accum + 1)
            head.value.isEmpty() ->
              commonLength(tail, start, accum + 1)
            (remaining.firstOrNull() as? PathSegment.Variable)?.accepts(head.value) == true ->
              commonLength(tail, start + 1, accum + 1)
            remaining.firstOrNull() is PathSegment.Undefined ->
              commonLength(tail, start + 1, accum + 1)
            else -> accum
          }
        }
        else -> {
          val aHead = remaining.firstOrNull() ?: return accum
          if (aHead !is PathSegment.Exact)
            return commonLength(tail, start + 1, accum + 1)
          else
            return commonLength(tail, start, accum + 1)
        }
      }
    }

    return commonLength(this.segments, 0, 0)
  }

  fun isCompatibleWith(another: UrlPath): Boolean {
    fun isEmptyExact(headA: PathSegment) = headA is PathSegment.Exact && headA.value == ""

    fun hasValue(another: List<PathSegment>) = !another.all { isEmptyExact(it) || it is PathSegment.Undefined }

    tailrec fun compatible(cur: List<PathSegment>, another: List<PathSegment>): Boolean {
      if (cur.isEmpty() && !hasValue(another)) return true
      if (another.isEmpty() && !hasValue(cur)) return true
      if (cur.isEmpty() && another.isEmpty()) return true
      val (headA, tailA) = cur.headTailOrNull() ?: return false
      val (headB, tailB) = another.headTailOrNull() ?: return false
      when {
        isEmptyExact(headA) -> return compatible(tailA, if (isEmptyExact(headB)) tailB else another)
        isEmptyExact(headB) -> return compatible(if (isEmptyExact(headA)) tailA else cur, tailB)
        headA is PathSegment.Undefined && headB is PathSegment.Undefined -> return compatible(tailA, tailB)
        headA is PathSegment.Undefined && hasValue(tailA) || headB is PathSegment.Undefined && hasValue(tailB) ->
          return compatible(tailA, tailB)
        headA is PathSegment.Undefined && !hasValue(tailA) -> return true
        headB is PathSegment.Undefined && !hasValue(tailB) -> return true
        headA is PathSegment.Variable && headB is PathSegment.Variable -> return compatible(tailA, tailB)
        headA is PathSegment.Variable && headB is PathSegment.Exact ->
          return if (headA.accepts(headB.value)) compatible(tailA, tailB) else false
        headA is PathSegment.Exact && headB is PathSegment.Variable ->
          return if (headB.accepts(headA.value)) compatible(tailA, tailB) else false
        headA is PathSegment.Exact && headB is PathSegment.Exact ->
          return if (headA.value == headB.value) compatible(tailA, tailB) else false
        headA is PathSegment.Composite && headB is PathSegment.Composite ->
          return if (UrlPath(headA.segments).isCompatibleWith(UrlPath(headB.segments))) compatible(tailA, tailB) else false
        else -> return false
      }
    }

    return compatible(this.segments, another.segments)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as UrlPath
    return segments == other.segments
  }

  override fun hashCode(): Int = segments.hashCode()

  companion object {
    val EMPTY: UrlPath = UrlPath(listOf(PathSegment.Exact("")))

    private const val STAR = "*"
    private const val UNKNOWN = "<???>"

    const val UNKNOWN_URL_PATH_SEGMENT_PRESENTATION: String = "\${..}"

    @JvmField
    val DEFAULT_PATH_VARIABLE_PRESENTATION: PathSegmentRenderer = object : PathSegmentRenderer {
      override fun visitVariable(variable: PathSegment.Variable): String = STAR
    }

    @JvmField
    val FULL_PATH_VARIABLE_PRESENTATION: PathSegmentRenderer = object : PathSegmentRenderer {
      override fun visitVariable(variable: PathSegment.Variable): String =
        buildString {
          if (variable.variableName.isNullOrEmpty()) {
            append(STAR)
          }
          else {
            append("{")
            append(variable.variableName)
            append("}")
          }
        }
    }

    @JvmStatic
    fun fromExactString(string: String): UrlPath =
      UrlPath(string.split("/").map { PathSegment.Exact(it) })

    @JvmStatic
    fun combinations(urlPath: UrlPath): Sequence<UrlPath> = sequence {
      yield(urlPath)

      if (urlPath.segments.firstOrNull()?.isEmpty() == true)
        yield(UrlPath(urlPath.segments.subList(1, urlPath.segments.size)))

      val undefined = urlPath.segments
                        .indexOfFirst { it == PathSegment.Undefined }
                        .takeIf { it != -1 } ?: return@sequence

      val starting = urlPath.segments.subList(0, undefined)
      val ending = urlPath.segments.subList(undefined + 1, urlPath.segments.size)
      yieldAll(combinations(UrlPath(starting + ending)))
    }
  }
}

class UrlSpecialSegmentMarker @JvmOverloads constructor(val prefix: String, val suffix: String, private val pattern: Pattern? = null) {

  fun matches(segment: CharSequence): Boolean = segment.startsWith(prefix) && segment.endsWith(suffix)

  private fun extractValue(segmentStr: CharSequence): ExtractionInfo? {
    if (!matches(segmentStr)) return null
    val body = segmentStr.substring(prefix.length, segmentStr.length - suffix.length)
    if (pattern != null) {
      val matcher = pattern.matcher(body)
      if (matcher.find()) {
        return ExtractionInfo(matcher.group(1), matcher)
      }
      return null
    }
    return ExtractionInfo(body, null)
  }

  data class ExtractionInfo(val value: String, private val matcher: Matcher?) {
    val regexGroups: List<String?> = matcher?.let { m ->
      object : AbstractList<String?>() {
        override val size: Int get() = m.groupCount() + 1
        override fun get(index: Int): String? = m.group(index)
      }
    } ?: emptyList()
  }

  fun extractAll(segmentStr: CharSequence): List<Pair<TextRange, ExtractionInfo>> =
    PlaceholderTextRanges.getPlaceholderRanges(segmentStr.toString(), prefix, suffix, true, true)
      .mapNotNull { range -> extractValue(range.subSequence(segmentStr))?.let { range to it } }
}

@JvmOverloads
fun filterBestUrlPathMatches(all: Iterable<UrlTargetInfo>, original: UrlPath? = null): Set<UrlTargetInfo> =
  MultiPathBestMatcher().addBestMatching(all, original).getResult()

class MultiPathBestMatcher {
  private val bestResolveMatches = HashSet<UrlTargetInfo>()
  private var longestMatchLength = 1

  fun addBestMatching(all: Iterable<UrlTargetInfo>, original: UrlPath? = null): MultiPathBestMatcher {
    for (urlTargetInfo in all) {
      val exactCount = original?.commonLength(urlTargetInfo.path) ?: urlTargetInfo.path.segments.count { it is UrlPath.PathSegment.Exact }
      if (exactCount > longestMatchLength) {
        longestMatchLength = exactCount
        bestResolveMatches.clear()
        bestResolveMatches.add(urlTargetInfo)
      }
      else if (exactCount == longestMatchLength)
        bestResolveMatches.add(urlTargetInfo)
    }
    return this
  }

  fun getResult(): Set<UrlTargetInfo> = bestResolveMatches
}

fun getEndpointUrlPresentation(path: PartiallyKnownString): String {
  return path.segments.joinToString { segment ->
    when (segment) {
      is StringEntry.Known -> segment.value
      is StringEntry.Unknown -> UrlPath.UNKNOWN_URL_PATH_SEGMENT_PRESENTATION
    }
  }
}