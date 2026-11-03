// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.JvmMethodMatcher.MethodInfo
import com.intellij.debugger.engine.JvmMethodMatcher.ResolvedMethodInfo
import com.intellij.debugger.jdi.MethodBytecodeUtil
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.MethodNode

/**
 * Matches smart-step targets collected from PSI with method calls in the bytecode of the current stack frame.
 *
 * Besides basic matching, this class classifies calls that have already been executed and calls that may be skipped by normal control flow.
 * The latter classification is optional because regular smart step does not filter conditional calls and does not need to build a CFG.
 */
@ApiStatus.Internal
class JavaSmartStepIntoBytecodeMatcher(
  private val bytecode: PreparedBytecode,
  private val debugProcess: DebugProcessImpl,
  private val targets: List<SmartStepTarget>,
  private val calculateConditionalTargets: Boolean,
) {
  /**
   * @property notFoundTargets immediate PSI targets for which no matching bytecode call was found
   * @property collidingTargets PSI targets that matched more than one bytecode call with the same ordinal
   * @property alreadyExecutedTargets targets whose call instruction precedes the current bytecode offset
   * @property conditionallyExecutedTargets targets that can be avoided by normal control flow; falls back to the old basic-block heuristic
   * when a precise CFG cannot be built
   */
  data class Result(
    val notFoundTargets: List<SmartStepTarget>,
    val collidingTargets: List<SmartStepTarget>,
    val alreadyExecutedTargets: Set<SmartStepTarget>,
    val conditionallyExecutedTargets: Set<SmartStepTarget>,
  )

  private val currentBytecodeOffset = bytecode.currentBytecodeOffset
  private val methodNode = bytecode.methodNode
  private val foundTargets = HashSet<SmartStepTarget>()
  private val collidingTargets = ArrayList<SmartStepTarget>()
  private val alreadyExecutedTargets = HashSet<SmartStepTarget>()
  private val anotherBasicBlockTargets = HashSet<SmartStepTarget>()
  private val targetOffsets = Object2IntOpenHashMap<SmartStepTarget>().also { it.defaultReturnValue(-1) }

  @RequiresReadLock
  fun match(): Result {
    for (call in bytecode.calls) {
      ProgressManager.checkCanceled()
      matchMethodInstruction(call)
    }
    val notFoundTargets = targets.filterTo(ArrayList()) {
      !it.needsBreakpointRequest() && it !in foundTargets
    }
    val conditionallyExecutedTargets =
      if (calculateConditionalTargets && anotherBasicBlockTargets.isNotEmpty()) {
        // Preserve the previous basic-block heuristic for unsupported bytecode or an incomplete instruction-offset map.
        findConditionallyExecutedTargets() ?: anotherBasicBlockTargets
      }
      else {
        emptySet()
      }
    return Result(
      notFoundTargets,
      collidingTargets.toList(),
      alreadyExecutedTargets.toSet(),
      conditionallyExecutedTargets.toSet(),
    )
  }

  private fun matchMethodInstruction(call: MethodCall) {
    for (target in targets) {
      val methodTarget = target as? MethodSmartStepTarget ?: continue
      if (methodTarget.ordinal != call.ordinal) continue
      if (!methodMatches(methodTarget.method, call)) continue

      if (!foundTargets.add(methodTarget)) {
        // A single PSI target must correspond to exactly one invocation; otherwise its ordinal is not sufficient to select it safely.
        collidingTargets.add(methodTarget)
      }
      else {
        targetOffsets.put(methodTarget, call.offset)
        if (call.offset < currentBytecodeOffset) {
          val expression = PsiTreeUtil.getParentOfType(methodTarget.highlightElement, PsiCallExpression::class.java)
          targets.filterTo(alreadyExecutedTargets) { target ->
            val targetElement = target.highlightElement
            targetElement != null && PsiTreeUtil.isAncestor(expression, targetElement, false)
          }
        }
        if (call.afterJump) {
          // This is the legacy approximation used when precise CFG analysis below is unavailable.
          anotherBasicBlockTargets.add(methodTarget)
        }
      }
    }
  }

  private fun methodMatches(method: PsiMethod, call: MethodCall): Boolean {
    val info = bytecode.methods.getValue(method)
    return info.matches(call.owner, call.name, call.descriptor, debugProcess) { it in call.compatibleClasses }
  }

  internal class MethodCall(
    val owner: String,
    val name: String,
    val descriptor: String,
    val ordinal: Int,
    val offset: Int,
    val afterJump: Boolean,
    val compatibleClasses: Set<String>,
  )

  /** JVM data for one suspended frame. Matching can reuse it when a read action restarts. */
  class PreparedBytecode private constructor(
    internal val currentBytecodeOffset: Int,
    internal val className: String,
    internal val methodNode: OffsetTrackingMethodNode,
    internal val calls: List<MethodCall>,
    internal val methods: Map<PsiMethod, ResolvedMethodInfo>,
  ) {
    companion object {
      @JvmStatic
      @RequiresReadLockAbsence
      fun prepare(location: Location, lines: Set<Int>, targetMethods: Map<PsiMethod, MethodInfo>,
                  debugProcess: DebugProcessImpl): PreparedBytecode {
        DebuggerManagerThreadImpl.assertIsManagerThread()
        val methods = targetMethods.mapValues { [_, method] -> method.resolve(debugProcess) }
        val methodsBySignature = methods.values.groupBy { it.method.name to it.signature }
        val referenceTypes = HashMap<String, List<ReferenceType>>()
        fun getReferenceTypes(name: String): List<ReferenceType> = referenceTypes.getOrPut(name) {
          location.virtualMachine().classesByName(name)
        }
        val compatibleClasses = HashMap<Pair<String, String>, Boolean>()
        fun getCompatibleClasses(owner: String, name: String, descriptor: String): Set<String> {
          return methodsBySignature[name to descriptor].orEmpty().asSequence()
            .mapNotNull { it.method.className }
            .filter { it != owner }
            .filterTo(HashSet()) { className ->
              compatibleClasses.getOrPut(owner to className) {
                getReferenceTypes(owner).any { DebuggerUtils.instanceOf(it, className) }
              }
            }
        }

        val currentOffset = Math.toIntExact(location.codeIndex())
        val methodNode = OffsetTrackingMethodNode(location.method())
        val calls = ArrayList<MethodCall>()
        val resolvedMethods = HashMap<Triple<String, String, String>, Method?>()
        MethodBytecodeUtil.visit(location.method(), object : MethodVisitor(Opcodes.API_VERSION, methodNode),
                                                           MethodBytecodeUtil.InstructionOffsetReader {
          private var lineMatch = false
          private var offset = -1
          private var endOfBasicBlock = Int.MAX_VALUE
          private val counter = Object2IntOpenHashMap<String>()

          override fun readBytecodeInstructionOffset(bytecodeInstructionOffset: Int) {
            offset = bytecodeInstructionOffset
            methodNode.readBytecodeInstructionOffset(bytecodeInstructionOffset)
          }

          override fun visitLineNumber(line: Int, start: Label) {
            super.visitLineNumber(line, start)
            // JVM line numbers are one-based; source lines are zero-based.
            lineMatch = line - 1 in lines
          }

          override fun visitJumpInsn(opcode: Int, label: Label) {
            super.visitJumpInsn(opcode, label)
            if (lineMatch && offset in currentOffset..<endOfBasicBlock) {
              assert(offset != -1)
              endOfBasicBlock = offset
            }
          }

          override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
            if (!lineMatch) return
            ProgressManager.checkCanceled()
            assert(offset != -1)
            val method = resolvedMethods.getOrPut(Triple(owner, name, descriptor)) {
              val referenceType = getReferenceTypes(Type.getObjectType(owner).className).firstOrNull()
              referenceType?.let { DebuggerUtils.findMethod(it, name, descriptor) }
            }
            val key = "${method?.declaringType()?.name() ?: owner}.$name$descriptor"
            val ordinal = counter.getInt(key)
            counter.put(key, Math.addExact(ordinal, 1))
            if (name.startsWith("access") && name.getOrNull(6) == '$') {
              // javac access$ bridges hide the source-level call one level deeper.
              if (method != null) {
                MethodBytecodeUtil.visit(method, object : MethodVisitor(Opcodes.API_VERSION) {
                  override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                    if (owner != "java/lang/AbstractMethodError") addCall(owner, name, descriptor, ordinal)
                  }
                }, false)
              }
            }
            else {
              addCall(owner, name, descriptor, ordinal)
            }
          }

          private fun addCall(owner: String, name: String, descriptor: String, ordinal: Int) {
            val className = owner.replace('/', '.')
            calls.add(MethodCall(className, name, descriptor, ordinal, offset, offset > endOfBasicBlock,
                                 getCompatibleClasses(className, name, descriptor)))
          }
        }, true)
        return PreparedBytecode(currentOffset, location.declaringType().name(), methodNode, calls, methods)
      }
    }
  }

  private fun findConditionallyExecutedTargets(): Set<SmartStepTarget>? {
    // LiteFramelessAnalyzer does not process JSR/RET subroutines. Falling back is safer for old bytecode that still contains them.
    if (methodNode.hasJsr) return null
    val currentInstruction = methodNode.getInstructionIndex(currentBytecodeOffset)
    if (currentInstruction < 0) return null

    return try {
      // JDI exposes bytecodes but not Code.max_stack/max_locals. Reconstructing them would require an additional data-flow pass,
      // while neither stack nor local-variable values are needed to build the CFG. Use the frameless analyzer instead.
      val graph = ControlFlowGraph.build(bytecode.className, methodNode, false)
      val result = HashSet<SmartStepTarget>()
      for ((target, offset) in targetOffsets.object2IntEntrySet()) {
        if (offset < currentBytecodeOffset) continue
        val targetInstruction = methodNode.getInstructionIndex(offset)
        if (targetInstruction < 0) return null
        if (canAvoidInstruction(currentInstruction, targetInstruction, graph)) {
          result.add(target)
        }
      }
      result
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      LOG.debug("Unable to build bytecode control flow graph for smart step into", e)
      null
    }
  }

  internal class OffsetTrackingMethodNode(method: Method) :
    MethodNode(Opcodes.API_VERSION, method.modifiers(), method.name(), method.signature(), null, null),
    MethodBytecodeUtil.InstructionOffsetReader {
    private val instructionIndices = Int2IntOpenHashMap().also { it.defaultReturnValue(-1) }
    var hasJsr = false
      private set

    override fun readBytecodeInstructionOffset(offset: Int) {
      // ClassReader reports the offset before visiting labels and the instruction at that offset.
      // Pointing to the first label is intentional: normal fall-through edges lead from it to the actual instruction.
      instructionIndices.put(offset, instructions.size())
    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
      super.visitJumpInsn(opcode, label)
      hasJsr = hasJsr || opcode == Opcodes.JSR
    }

    fun getInstructionIndex(bytecodeOffset: Int): Int = instructionIndices.get(bytecodeOffset)
  }

  companion object {
    private val LOG = Logger.getInstance(JavaSmartStepIntoBytecodeMatcher::class.java)

    private fun canAvoidInstruction(instruction: Int, targetInstruction: Int, graph: ControlFlowGraph): Boolean {
      if (instruction == targetInstruction) return false

      // Explore normal control flow while treating the target as a barrier. Reaching a normal exit proves that the target can be skipped.
      val instructionCount = graph.transitions.size
      val reachable = BooleanArray(instructionCount)
      val reachableInstructions = IntArray(instructionCount)
      var reachableCount = 0
      var nextInstruction = 0
      reachable[instruction] = true
      reachableInstructions[reachableCount++] = instruction
      while (nextInstruction < reachableCount) {
        val current = reachableInstructions[nextInstruction++]
        var hasNormalSuccessor = false
        for (successor in graph.transitions[current]) {
          // Exception handlers do not represent ordinary execution choices and must not make a target look optional.
          if (ControlFlowGraph.Edge(current, successor) in graph.errorTransitions) continue
          // Count an edge to the target as a real successor even though traversal stops there; otherwise its predecessor would look like an exit.
          hasNormalSuccessor = true
          if (successor != targetInstruction && !reachable[successor]) {
            reachable[successor] = true
            reachableInstructions[reachableCount++] = successor
          }
        }
        if (!hasNormalSuccessor) return true
      }

      // No exit was reachable without the target. The remaining way to avoid it is to stay in a cycle forever.
      // Kahn's algorithm processes the acyclic part; any unprocessed reachable nodes prove that such a cycle exists.
      val incomingEdges = IntArray(instructionCount)
      for (index in 0 until reachableCount) {
        val current = reachableInstructions[index]
        for (successor in graph.transitions[current]) {
          if (successor != targetInstruction && reachable[successor] &&
              ControlFlowGraph.Edge(current, successor) !in graph.errorTransitions) {
            incomingEdges[successor]++
          }
        }
      }

      val queue = IntArray(reachableCount)
      var queueStart = 0
      var queueEnd = 0
      for (index in 0 until reachableCount) {
        val current = reachableInstructions[index]
        if (incomingEdges[current] == 0) queue[queueEnd++] = current
      }
      var processedCount = 0
      while (queueStart < queueEnd) {
        val current = queue[queueStart++]
        processedCount++
        for (successor in graph.transitions[current]) {
          if (successor != targetInstruction && reachable[successor] &&
              ControlFlowGraph.Edge(current, successor) !in graph.errorTransitions && --incomingEdges[successor] == 0) {
            queue[queueEnd++] = successor
          }
        }
      }
      return processedCount < reachableCount
    }
  }
}
