// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.configurationStore.schemeManager

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.configurationStore.LOG
import com.intellij.openapi.options.ExternalizableScheme
import com.intellij.openapi.options.Scheme
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.UniqueNameGenerator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference

internal class SchemeCollection<T : Any>(
  @JvmField val list: MutableList<T>,
  // the scheme could be changed - so, hashcode will be changed - we must use identity hashing strategy
  @JvmField val schemeToInfo: ConcurrentMap<T, ExternalInfo> = ConcurrentCollectionFactory.createConcurrentIdentityMap()
) {
  fun putSchemeInfo(scheme: T, externalInfo: ExternalInfo): ExternalInfo? {
    return schemeToInfo.put(scheme, externalInfo)
  }
}

private fun <T : Any> newSchemeCollection(): SchemeCollection<T> {
  return SchemeCollection(list = ContainerUtil.createLockFreeCopyOnWriteList(),
                          schemeToInfo = ConcurrentCollectionFactory.createConcurrentIdentityMap())
}

internal fun <T : Any> toSchemeCollection(list: List<T>, schemeToInfo: Map<T, ExternalInfo>): SchemeCollection<T> {
  return SchemeCollection(list = ContainerUtil.createLockFreeCopyOnWriteList(list),
                          schemeToInfo = ConcurrentCollectionFactory.createConcurrentIdentityMap<T, ExternalInfo>().also {
                            it.putAll(schemeToInfo)
                          })
}

internal class SchemeListManager<T : Scheme>(private val schemeManager: SchemeManagerImpl<T, *>) {
  private val schemeListRef: AtomicReference<SchemeCollection<T>> = AtomicReference(newSchemeCollection())

  internal val readOnlyExternalizableSchemes = ConcurrentHashMap<String, T>()

  val schemes: MutableList<T>
    get() = schemeListRef.get().list

  val data: SchemeCollection<T>
    get() = schemeListRef.get()

  fun replaceSchemeList(oldList: SchemeCollection<T>, newList: SchemeCollection<T>) {
    if (!schemeListRef.compareAndSet(oldList, newList)) {
      throw IllegalStateException("Scheme list was modified")
    }
  }

  fun getExternalInfo(scheme: T): ExternalInfo? = schemeListRef.get().schemeToInfo.get(scheme)

  fun addScheme(scheme: T, replaceExisting: Boolean) {
    var toReplace = -1
    val schemes = schemes
    val processor = schemeManager.processor
    val schemeToInfo = schemeListRef.get().schemeToInfo
    for ((index, existing) in schemes.withIndex()) {
      if (processor.getSchemeKey(existing) != processor.getSchemeKey(scheme)) {
        continue
      }

      toReplace = index
      if (existing === scheme) {
        // do not just return, below a scheme will be removed from `filesToDelete` list
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
        (scheme as ExternalizableScheme).renameScheme(UniqueNameGenerator.generateUniqueName(scheme.name, collectExistingNames(schemes)))
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

  fun setSchemes(newSchemes: List<T>, newCurrentScheme: T?, removeCondition: ((T) -> Boolean)?) {
    val oldList = schemeListRef.get()

    // we must not use remove or removeAll to avoid "equals" call
    val newSchemesMutable = if (removeCondition == null) {
      ContainerUtil.createConcurrentList(newSchemes)
    }
    else {
      val list = ContainerUtil.createConcurrentList<T>()
      oldList.list.filterTo(list) { !removeCondition(it) }
      list.addAll(newSchemes)
      list
    }
    val newSchemeToInfo = ConcurrentCollectionFactory.createConcurrentIdentityMap<T, ExternalInfo>().also {
      it.putAll(oldList.schemeToInfo)
    }
    schemeManager.retainExternalInfo(isScheduleToDelete = true, schemeToInfo = newSchemeToInfo, newSchemes = newSchemesMutable)

    val newList = SchemeCollection(list = ContainerUtil.createConcurrentList(newSchemesMutable), schemeToInfo = newSchemeToInfo)
    replaceSchemeList(oldList, newList)

    val oldCurrentScheme = schemeManager.activeScheme
    if (oldCurrentScheme != newCurrentScheme) {
      val newScheme: T?
      if (newCurrentScheme != null) {
        schemeManager.activeScheme = newCurrentScheme
        newScheme = newCurrentScheme
      }
      else if (oldCurrentScheme != null && !newSchemesMutable.contains(oldCurrentScheme)) {
        newScheme = newSchemesMutable.firstOrNull()
        schemeManager.activeScheme = newScheme
      }
      else {
        newScheme = null
      }

      if (oldCurrentScheme != newScheme) {
        schemeManager.processor.onCurrentSchemeSwitched(oldScheme = oldCurrentScheme,
                                                        newScheme = newScheme,
                                                        processChangeSynchronously = false)
      }
    }
  }

  private fun collectExistingNames(schemes: Collection<T>): Collection<String> {
    return schemes.mapTo(HashSet(schemes.size)) { schemeManager.processor.getSchemeKey(it) }
  }
}

private fun ExternalizableScheme.renameScheme(newName: String) {
  if (newName != name) {
    name = newName
    LOG.assertTrue(newName == name)
  }
}