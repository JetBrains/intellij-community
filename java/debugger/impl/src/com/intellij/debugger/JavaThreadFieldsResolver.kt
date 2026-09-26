// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.openapi.diagnostic.logger
import com.sun.jdi.BooleanType
import com.sun.jdi.BooleanValue
import com.sun.jdi.ClassNotLoadedException
import com.sun.jdi.ClassType
import com.sun.jdi.Field
import com.sun.jdi.IntegerType
import com.sun.jdi.IntegerValue
import com.sun.jdi.LongType
import com.sun.jdi.LongValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.ThreadReference
import com.sun.jdi.Type
import com.sun.jdi.Value
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches the layout of some fields of `java.lang.Thread` and finds their values.
 */
@ApiStatus.Internal
class JavaThreadFieldsResolver {

  @Volatile
  private var jlThread: ClassType? = null
  private val lookupCache = ConcurrentHashMap<LookupKey, ThreadFieldLookup>()

  // sync API

  fun isJavaThreadDaemon(threadReference: ThreadReference): Boolean {
    if (threadReference.isVirtual) return true
    return getJavaThreadFieldValue(threadReference, DAEMON_FIELD_NAMES, BooleanType::class.java)?.let {
      (it as BooleanValue).booleanValue()
    } ?: false
  }

  fun getJavaThreadPriority(threadReference: ThreadReference): Int? =
    getJavaThreadFieldValue(threadReference, PRIORITY_FIELD_NAMES, IntegerType::class.java)?.let {
      (it as IntegerValue).intValue()
    }

  fun getJavaThreadId(threadReference: ThreadReference): Long? =
    getJavaThreadFieldValue(threadReference, TID_FIELD_NAMES, LongType::class.java)?.let {
      (it as LongValue).longValue()
    }

  fun getJavaThreadContainer(threadReference: ThreadReference): ObjectReference? =
    getJavaThreadFieldValue(threadReference, CONTAINER_FIELD_NAMES, ClassType::class.java, optional = true)?.let {
      (it as ObjectReference)
    }

  /**
   * Reads a field value from a [ThreadReference] by field name and type.
   *
   * Since Project Loom, some [java.lang.Thread] fields (e.g. `daemon`, `priority`, `tid`)
   * have been moved into an inner [Thread.FieldHolder] object. This method handles both layouts:
   * it first looks for the field directly in the thread's type, and falls back to searching inside [Thread.FieldHolder].
   *
   * @param threadReference the thread to read the field from
   * @param fieldNames      candidate field names to look up (e.g. `["daemon", "isDaemon"]`)
   * @param expectedType    expected JDI type of the field (e.g. [com.sun.jdi.BooleanType])
   * @param optional        if `false`, logs an error when the field is not found
   * @return the field value, or `null` if the field was not found or could not be read
   */
  private fun getJavaThreadFieldValue(
    threadReference: ThreadReference,
    fieldNames: List<String>,
    expectedType: Class<out Type>,
    optional: Boolean = false,
  ): Value? {
    val jlThreadType = resolveJavaLangThread(threadReference) ?: return null
    return when (val lookup = lookupFor(jlThreadType, fieldNames, expectedType, optional)) {
      is ThreadFieldLookup.Direct -> threadReference.getValue(lookup.field)
      is ThreadFieldLookup.Holder -> {
        val holderValue = threadReference.getValue(lookup.holder)
        (holderValue as? ObjectReference)?.getValue(lookup.inHolder)
      }
      is ThreadFieldLookup.Missing, null -> null
    }
  }

  // async API

  fun isJavaThreadDaemonAsync(threadReference: ThreadReference): CompletableFuture<Boolean> {
    if (threadReference.isVirtual) return CompletableFuture.completedFuture(true)
    return getJavaThreadFieldValueAsync(threadReference, DAEMON_FIELD_NAMES, BooleanType::class.java)
      .thenApply { value -> (value as BooleanValue?)?.booleanValue() ?: false }
  }

  private fun getJavaThreadFieldValueAsync(
    threadReference: ThreadReference,
    fieldNames: List<String>,
    expectedType: Class<out Type>,
    optional: Boolean = false,
  ): CompletableFuture<Value?> {
    return resolveJavaLangThreadAsync(threadReference).thenCompose { jlThreadType ->
      if (jlThreadType == null) return@thenCompose CompletableFuture.completedFuture(null)
      when (val lookup = lookupFor(jlThreadType, fieldNames, expectedType, optional)) {
        is ThreadFieldLookup.Direct -> DebuggerUtilsAsync.getValue(threadReference, lookup.field)
        is ThreadFieldLookup.Holder -> DebuggerUtilsAsync.getValue(threadReference, lookup.holder).thenCompose { holderValue ->
          if (holderValue is ObjectReference) DebuggerUtilsAsync.getValue(holderValue, lookup.inHolder)
          else CompletableFuture.completedFuture(null)
        }
        else -> CompletableFuture.completedFuture(null)
      }
    }
  }

  private fun resolveJavaLangThread(threadReference: ThreadReference): ClassType? {
    jlThread?.let { return it }
    return findJavaLangThread(threadReference.referenceType()).also {
      jlThread = it
    }
  }

  private fun resolveJavaLangThreadAsync(threadReference: ThreadReference): CompletableFuture<ClassType?> {
    jlThread?.let { return CompletableFuture.completedFuture(it) }
    return DebuggerUtilsAsync.type(threadReference).thenCompose { type ->
      val refType = type as? ReferenceType
                    ?: return@thenCompose CompletableFuture.completedFuture(null)
      findJavaLangThreadAsync(refType).thenApply { resolved ->
        jlThread = resolved
        resolved
      }
    }
  }

  private fun findJavaLangThread(threadType: ReferenceType): ClassType? =
    // Don't use `threadType` itself to search fields, because it can override base ones, see IDEA-374608.
    generateSequence(threadType as? ClassType) { it.superclass() }
      .firstOrNull { it.name() == JAVA_LANG_THREAD }
      .also { if (it == null) logJavaLangThreadNotFound(threadType) }

  private fun findJavaLangThreadAsync(threadType: ReferenceType): CompletableFuture<ClassType?> {
    fun walk(current: ClassType?): CompletableFuture<ClassType?> {
      if (current == null) {
        logJavaLangThreadNotFound(threadType)
        return CompletableFuture.completedFuture(null)
      }
      if (current.name() == JAVA_LANG_THREAD) return CompletableFuture.completedFuture(current)
      return DebuggerUtilsAsync.superclass(current).thenCompose { walk(it) }
    }
    return walk(threadType as? ClassType)
  }

  private fun logJavaLangThreadNotFound(threadType: ReferenceType) {
    logger<JavaThreadFieldsResolver>().error("$threadType is expected to have $JAVA_LANG_THREAD as super type")
  }

  private fun lookupFor(
    jlThreadType: ClassType,
    fieldNames: List<String>,
    expectedType: Class<out Type>,
    optional: Boolean,
  ): ThreadFieldLookup? {
    val key = LookupKey(fieldNames, expectedType)
    return lookupCache.getOrPut(key) {
      findThreadFieldLookup(jlThreadType, fieldNames, expectedType, optional) ?: ThreadFieldLookup.Missing
    }.takeUnless { it is ThreadFieldLookup.Missing }
  }

  /**
   * Locates where to read a Thread field from: directly on `java.lang.Thread`,
   * or — since Project Loom — inside its inner `FieldHolder`. Logs an error
   * when nothing is found and [optional] is `false`.
   */
  private fun findThreadFieldLookup(
    jlThreadType: ClassType,
    fieldNames: List<String>,
    expectedType: Class<out Type>,
    optional: Boolean,
  ): ThreadFieldLookup? {
    val direct = findFieldOfType(fieldNames, jlThreadType, expectedType)
    if (direct != null) return ThreadFieldLookup.Direct(direct)

    val holder = findFieldOfType(listOf("holder"), jlThreadType, ClassType::class.java)
    if (holder != null) {
      val inHolder = findFieldOfType(fieldNames, holder.type() as ClassType, expectedType)
      if (inHolder != null) return ThreadFieldLookup.Holder(holder, inHolder)
    }

    if (!optional) {
      val vm = jlThreadType.virtualMachine()
      val searchedTypes = if (holder != null) "$jlThreadType and its FieldHolder" else jlThreadType.toString()
      logger<JavaThreadFieldsResolver>().error(
        searchedTypes + (if (holder != null) " have " else " has ") +
        "none of fields " + fieldNames + ". VM: " + vm.name() + ", " + vm.version()
      )
    }
    return null
  }

  private fun findFieldOfType(
    fieldNames: List<String>,
    typeToSearch: ReferenceType,
    expectedType: Class<out Type>,
  ): Field? {
    val wellNamedFields = fieldNames.mapNotNull { DebuggerUtils.findField(typeToSearch, it) }
    if (wellNamedFields.isEmpty()) return null

    val wellTypedFields = try {
      wellNamedFields.filter { field ->
        expectedType.isAssignableFrom(field.type().javaClass)
      }
    } catch (_: ClassNotLoadedException) {
      logger<JavaThreadFieldsResolver>().info(
        "$typeToSearch has the fields ${wellNamedFields.map { it.name() }} whose type is not yet loaded, skipping. " +
        "VM: ${typeToSearch.virtualMachine().let { "${it.name()}, ${it.version()}" }}"
      )
      return null
    }

    if (wellTypedFields.isEmpty()) {
      val vm = typeToSearch.virtualMachine()
      logger<JavaThreadFieldsResolver>().error(
        "$typeToSearch has following fields ${wellNamedFields.map { it.name() }} with unexpected types ${wellNamedFields.map { it.type() }}, skipping it. " +
        "VM: ${vm.name()}, ${vm.version()}.")
      return null
    }

    if (wellTypedFields.size > 1) {
      val vm = typeToSearch.virtualMachine()
      logger<JavaThreadFieldsResolver>().error(
        "$typeToSearch has ambiguous list of fields ${wellTypedFields.map { it.name() }}, taking the first one. " +
        "VM: ${vm.name()}, ${vm.version()}.")
    }

    return wellTypedFields.first()
  }

  private data class LookupKey(val fieldNames: List<String>, val expectedType: Class<out Type>)

  private sealed class ThreadFieldLookup {
    data class Direct(val field: Field) : ThreadFieldLookup()
    data class Holder(val holder: Field, val inHolder: Field) : ThreadFieldLookup()
    object Missing : ThreadFieldLookup()
  }

  companion object {
    private const val JAVA_LANG_THREAD = "java.lang.Thread"
    private val DAEMON_FIELD_NAMES = listOf("daemon", "isDaemon")
    private val PRIORITY_FIELD_NAMES = listOf("priority")
    private val TID_FIELD_NAMES = listOf("tid")
    private val CONTAINER_FIELD_NAMES = listOf("container")
  }
}
