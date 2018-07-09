package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.LOG
import com.intellij.configurationStore.SchemeManagerImpl
import com.intellij.openapi.options.ExternalizableScheme
import com.intellij.openapi.util.Condition
import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.filterSmart
import com.intellij.util.text.UniqueNameGenerator
import gnu.trove.THashSet
import java.util.concurrent.atomic.AtomicReference

internal class SchemeListManager<T : Any>(private val schemeManager: SchemeManagerImpl<T, *>) {
  private val schemesRef = AtomicReference(ContainerUtil.createLockFreeCopyOnWriteList<T>() as ConcurrentList<T>)

  val readOnlyExternalizableSchemes = ContainerUtil.newConcurrentMap<String, T>()

  val schemes: ConcurrentList<T>
    get() = schemesRef.get()

  fun replaceSchemeList(oldList: ConcurrentList<T>, newList: List<T>) {
    if (!schemesRef.compareAndSet(oldList, ContainerUtil.createLockFreeCopyOnWriteList(newList) as ConcurrentList<T>)) {
      throw IllegalStateException("Scheme list was modified")
    }
  }

  fun addScheme(scheme: T, replaceExisting: Boolean) {
    var toReplace = -1
    val schemes = schemes
    val processor = schemeManager.processor
    val schemeToInfo = schemeManager.schemeToInfo
    for ((index, existing) in schemes.withIndex()) {
      if (processor.getSchemeKey(existing) != processor.getSchemeKey(scheme)) {
        continue
      }

      toReplace = index
      if (existing === scheme) {
        // do not just return, below scheme will be removed from filesToDelete list
        break
      }

      if (existing.javaClass != scheme.javaClass) {
        LOG.warn("'${processor.getSchemeKey(scheme)}' ${existing.javaClass.simpleName} replaced with ${scheme.javaClass.simpleName}")
      }

      if (replaceExisting && processor.isExternalizable(existing)) {
        val oldInfo = schemeToInfo.remove(existing)
        if (oldInfo != null && processor.isExternalizable(scheme) && !schemeToInfo.containsKey(scheme)) {
          schemeToInfo.put(scheme, oldInfo)
        }
      }
    }

    when {
      toReplace == -1 -> schemes.add(scheme)
      (replaceExisting || !processor.isExternalizable(scheme)) -> {
        if (schemes.get(toReplace) !== scheme) {
          // avoid "set" (LockFreeCopyOnWriteArrayList calls ARRAY_UPDATER.compareAndSet and so on)
          schemes.set(toReplace, scheme)
        }
      }
      else -> {
        (scheme as ExternalizableScheme).renameScheme(
          UniqueNameGenerator.generateUniqueName(scheme.name, collectExistingNames(schemes)))
        schemes.add(scheme)
      }
    }

    if (processor.isExternalizable(scheme) && schemeManager.filesToDelete.isNotEmpty()) {
      schemeToInfo.get(scheme)?.let {
        schemeManager.filesToDelete.remove(it.fileName)
      }
    }

    schemeManager.processPendingCurrentSchemeName(scheme)
  }

  fun setSchemes(newSchemes: List<T>, newCurrentScheme: T?, removeCondition: Condition<T>?) {
    if (schemes.isNotEmpty()) {
      if (removeCondition == null) {
        schemes.clear()
      }
      else {
        // we must not use remove or removeAll to avoid "equals" call
        schemesRef.set(ContainerUtil.createConcurrentList(schemes.filterSmart { !removeCondition.value(it) }))
      }
    }

    schemes.addAll(newSchemes)

    val oldCurrentScheme = schemeManager.activeScheme
    schemeManager.retainExternalInfo()

    if (oldCurrentScheme != newCurrentScheme) {
      val newScheme: T?
      if (newCurrentScheme != null) {
        schemeManager.activeScheme = newCurrentScheme
        newScheme = newCurrentScheme
      }
      else if (oldCurrentScheme != null && !schemes.contains(oldCurrentScheme)) {
        newScheme = schemes.firstOrNull()
        schemeManager.activeScheme = newScheme
      }
      else {
        newScheme = null
      }

      if (oldCurrentScheme != newScheme) {
        schemeManager.processor.onCurrentSchemeSwitched(oldCurrentScheme, newScheme)
      }
    }
  }

  private fun collectExistingNames(schemes: Collection<T>): Collection<String> {
    val result = THashSet<String>(schemes.size)
    schemes.mapTo(result) { schemeManager.processor.getSchemeKey(it) }
    return result
  }

  fun removeFirstScheme(schemes: MutableList<T>, scheduleDelete: Boolean = true, condition: (T) -> Boolean): T? {
    val iterator = schemes.iterator()
    for (scheme in iterator) {
      if (!condition(scheme)) {
        continue
      }

      if (schemeManager.activeScheme === scheme) {
        schemeManager.activeScheme = null
      }

      iterator.remove()

      if (scheduleDelete && schemeManager.processor.isExternalizable(scheme)) {
        schemeManager.schemeToInfo.remove(scheme)?.let(schemeManager::scheduleDelete)
      }
      return scheme
    }

    return null
  }
}