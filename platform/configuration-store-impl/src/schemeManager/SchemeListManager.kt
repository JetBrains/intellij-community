package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.SchemeManagerImpl
import com.intellij.openapi.util.Condition
import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.filterSmart
import gnu.trove.THashSet
import java.util.concurrent.atomic.AtomicReference

internal class SchemeListManager<T : Any>(private val schemeManager: SchemeManagerImpl<T, *>) {
  val schemesRef = AtomicReference(ContainerUtil.createLockFreeCopyOnWriteList<T>() as ConcurrentList<T>)

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

  fun collectExistingNames(schemes: Collection<T>): Collection<String> {
    val result = THashSet<String>(schemes.size)
    schemes.mapTo(result) { schemeManager.processor.getSchemeKey(it) }
    return result
  }
}