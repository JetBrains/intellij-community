package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.SchemeManagerImpl
import com.intellij.openapi.util.Condition
import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.filterSmart
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

    val oldCurrentScheme = schemeManager.currentScheme
    schemeManager.retainExternalInfo()

    if (oldCurrentScheme != newCurrentScheme) {
      val newScheme: T?
      if (newCurrentScheme != null) {
        schemeManager.currentScheme = newCurrentScheme
        newScheme = newCurrentScheme
      }
      else if (oldCurrentScheme != null && !schemes.contains(oldCurrentScheme)) {
        newScheme = schemes.firstOrNull()
        schemeManager.currentScheme = newScheme
      }
      else {
        newScheme = null
      }

      if (oldCurrentScheme != newScheme) {
        schemeManager.processor.onCurrentSchemeSwitched(oldCurrentScheme, newScheme)
      }
    }
  }

  fun clearAllSchemes() {
    for (it in schemeManager.schemeToInfo.values) {
      schemeManager.scheduleDelete(it)
    }

    schemeManager.currentScheme = null
    schemes.clear()
    schemeManager.schemeToInfo.clear()
  }

  fun collectExistingNames(schemes: Collection<T>): Collection<String> {
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

      if (schemeManager.currentScheme === scheme) {
        schemeManager.currentScheme = null
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