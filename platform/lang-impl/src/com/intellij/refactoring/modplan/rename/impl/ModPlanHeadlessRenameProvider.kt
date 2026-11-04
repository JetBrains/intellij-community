// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.rename.impl

import com.intellij.lang.Language
import com.intellij.model.SymbolPointer
import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanQuestion
import com.intellij.refactoring.modplan.rename.ModPlanBridgeRenameProvider
import com.intellij.refactoring.modplan.rename.ModPlanRenameLanguages
import com.intellij.refactoring.rename.HeadlessRenamePsiElementProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase
import com.intellij.refactoring.rename.RenamePsiElementProcessorCore
import com.intellij.refactoring.rename.isHeadlessRenameSupported
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import java.lang.reflect.Method

/**
 * The provider of the legacy processors that cannot be registered on their own: the default processor, which the legacy rename takes for an
 * element that no processor claims, and the [HeadlessRenamePsiElementProcessor]s. A processor that can be registered has its own subclass
 * of [ModPlanBridgeRenameProvider], such as [com.intellij.refactoring.rename.RenamePsiFileProcessor.ModPlanRenamePsiFileProvider].
 *
 * The default processor renames an element only in a language that the headless rename covers, as in another language a processor that
 * claims the element may be missing, and only if no headless processor claims the element. Else the first headless processor that claims
 * the element owns it, in Java, in Kotlin or with no language, if its processor writes as the default write. An element with no language
 * is a fake element, such as the path variable that a Kotlin parameter adds to the rename. Each hook uses the
 * first headless processor of the element, and else the default processor.
 * It is registered last, so that a registered provider wins over it.
 * Only the other hooks need the ownership: the engine calls [prepare] on every provider, see [shouldPrepareWith].
 *
 * It is transitional: remove it together with the [HeadlessRenamePsiElementProcessor] extension point, when every headless processor has
 * a registered provider. Before that, give the default processor a registered subclass of [ModPlanBridgeRenameProvider], as the one of
 * the files has, as else its elements lose the ModPlan rename.
 */
@ApiStatus.Internal
class ModPlanHeadlessRenameProvider : ModPlanBridgeRenameProvider(RenamePsiElementProcessor.DEFAULT.javaClass) {
  override fun isOwner(subject: SymbolPointer): Boolean {
    val element = subject.element() ?: return false
    if (!isHeadlessRenameSupported(element.language)) return false
    val owner = headlessProcessorsOf(element).firstOrNull() ?: return RenamePsiElementProcessor.forElement(element) === RenamePsiElementProcessor.DEFAULT
    val language = element.language
    return (ModPlanRenameLanguages.isJvmLanguage(language) || language === Language.ANY) &&
           WRITES_AS_DEFAULT.get(owner.processorCore().javaClass)
  }

  override fun substitute(element: PsiElement, answers: ModPlanOptionValues): PsiElement? {
    val owner = headlessProcessorsOf(element).firstOrNull() ?: return super.substitute(element, answers)
    return owner.substituteElementToRenameHeadless(element)
  }

  /**
   * The headless processors are not registered as processors, so they add to the rename of every element that a headless processor claims,
   * also if another provider owns it. The default processor renames nothing with an element.
   */
  override fun shouldPrepareWith(subject: SymbolPointer): Boolean {
    val element = subject.element() ?: return false
    if (!isHeadlessRenameSupported(element.language)) return false
    val owner = headlessProcessorsOf(element).firstOrNull() ?: return false
    if (!ModPlanRenameLanguages.isJvmLanguage(element.language)) return false
    //todo the preparation writes nothing, so every headless processor must prepare, as legacy `allForElement`; remove the exception then
    return WRITES_AS_DEFAULT.get(owner.processorCore().javaClass) || owner.javaClass.name in PREPARING_PROCESSORS
  }

  override fun prepareRenaming(element: PsiElement, newName: String, renamed: MutableMap<PsiElement, String>, answers: ModPlanOptionValues) {
    val owners = headlessProcessorsOf(element)
    if (owners.isEmpty()) {
      super.prepareRenaming(element, newName, renamed, answers)
    }
    else {
      owners.forEach { it.prepareRenamingHeadless(element, newName, renamed) }
    }
  }

  override fun nameConflicts(element: PsiElement, newName: String, renamed: MutableMap<PsiElement, String>): MultiMap<PsiElement, String> {
    val owner = headlessProcessorsOf(element).firstOrNull() ?: return super.nameConflicts(element, newName, renamed)
    val conflicts = MultiMap<PsiElement, String>()
    owner.findExistingNameConflictsHeadless(element, newName, conflicts, renamed)
    return conflicts
  }

  override fun core(element: PsiElement): RenamePsiElementProcessorCore =
    headlessProcessorsOf(element).firstOrNull()?.processorCore() ?: super.core(element)

  override fun searchStorage(target: SymbolPointer): ModPlanQuestion.Storage? {
    val owner = target.element()?.let { headlessProcessorsOf(it).firstOrNull() } ?: return super.searchStorage(target)
    return (owner.processorCore() as? RenamePsiElementProcessorBase)?.let { SearchStorage(target, it) }
  }
}

/**
 * Whether a class of a processor writes as the default write: it declares neither `renameElement` nor a post-rename callback. A headless
 * registration does not state it, as the headless rename writes with the processor, so this provider checks it.
 */
private val WRITES_AS_DEFAULT = object : ClassValue<Boolean>() {
  override fun computeValue(type: Class<*>): Boolean {
    return try {
      isDefault(type.getMethod("renameElement", PsiElement::class.java, String::class.java, Array<UsageInfo>::class.java,
                               RefactoringElementListener::class.java)) &&
      isDefault(type.getMethod("getPostRenameCallback", PsiElement::class.java, String::class.java,
                               RefactoringElementListener::class.java)) &&
      isDefault(type.getMethod("getPostRenameCallback", PsiElement::class.java, String::class.java, Collection::class.java, Map::class.java,
                               RefactoringElementListener::class.java))
    }
    catch (_: NoSuchMethodException) {
      false
    }
  }

  private fun isDefault(method: Method): Boolean =
    method.declaringClass == RenamePsiElementProcessorBase::class.java || method.declaringClass == RenamePsiElementProcessorCore::class.java
}

/**
 * Temporary: the class names of the headless processors that prepare the rename, but do not write as the default write, see
 * [ModPlanHeadlessRenameProvider.shouldPrepareWith].
 */
private val PREPARING_PROCESSORS: Set<String> = setOf(
  // Adds the Lombok accessors to the rename of a field.
  "de.plushnikov.intellij.plugin.extension.LombokRenameFieldReferenceProcessor",
)

private fun headlessProcessorsOf(element: PsiElement): List<HeadlessRenamePsiElementProcessor> =
  buildList {
    HeadlessRenamePsiElementProcessor.EP_NAME.forEachExtensionSafe { if (it.canProcessElementHeadless(element)) add(it) }
  }
