// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.find.FindUtil
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.IPopupChooserBuilder
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.util.Ref
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.list.buildTargetPopupWithMultiSelect
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageView
import com.intellij.util.containers.map2Array
import java.awt.event.MouseEvent
import java.util.function.*

class PsiTargetNavigator<T: PsiElement>(val supplier: Supplier<Collection<T>>) {

  constructor(elements: List<T>) : this(Supplier { elements })
  constructor(elements: Array<T>) : this(Supplier { elements.toList() })

  private var selection: PsiElement? = null
  private var presentationProvider: TargetPresentationProvider<T> = TargetPresentationProvider { targetPresentation(it) }
  private var elementsConsumer: BiConsumer<Collection<T>, PsiTargetNavigator<T>>? = null
  private var builderConsumer: Consumer<IPopupChooserBuilder<ItemWithPresentation>>? = null
  private var title: @PopupTitle String? = null
  private var tabTitle: @TabTitle String? = null
  private var updater: BackgroundUpdaterTaskBase<ItemWithPresentation>? = null

  fun selection(selection: PsiElement?): PsiTargetNavigator<T> = apply { this.selection = selection }
  fun presentationProvider(provider: TargetPresentationProvider<T>): PsiTargetNavigator<T> = apply { this.presentationProvider = provider }
  fun elementsConsumer(consumer: BiConsumer<Collection<T>, PsiTargetNavigator<T>>): PsiTargetNavigator<T> = apply { elementsConsumer = consumer }
  fun builderConsumer(consumer: Consumer<IPopupChooserBuilder<ItemWithPresentation>>): PsiTargetNavigator<T> = apply { builderConsumer = consumer }
  fun title(title: @PopupTitle String?): PsiTargetNavigator<T> = apply { this.title = title }
  fun tabTitle(title: @TabTitle String?): PsiTargetNavigator<T> = apply { this.tabTitle = title }
  fun updater(updater: TargetUpdaterTask): PsiTargetNavigator<T> = apply { this.updater = updater }

  fun createPopup(project: Project, @PopupTitle title: String?): JBPopup {
    return createPopup(project, title) { element -> EditSourceUtil.navigateToPsiElement(element) }
  }

  fun createPopup(project: Project, @PopupTitle title: String?, processor: PsiElementProcessor<T>): JBPopup {
    val (items, selected) = computeItems(project)
    return buildPopup(items, title, project, selected, getPredicate(processor))
  }

  fun navigate(editor: Editor, @PopupTitle title: String?, processor: PsiElementProcessor<T>): Boolean {
    return navigate(editor.project!!, title, processor, Consumer { it.showInBestPositionFor(editor) })
  }

  fun navigate(editor: Editor, @PopupTitle title: String?): Boolean {
    return navigate(editor.project!!, title, { element -> EditSourceUtil.navigateToPsiElement(element) }, Consumer { it.showInBestPositionFor(editor) })
  }

  fun navigate(e: MouseEvent, @PopupTitle title: String?, project: Project): Boolean {
    return navigate(project, title, { element -> EditSourceUtil.navigateToPsiElement(element) }, Consumer { it.show(RelativePoint(e)) })
  }

  fun navigate(point: RelativePoint?, @PopupTitle title: String?, project: Project, processor: (element: T) -> Boolean): Boolean {
    return navigate(project, title, processor, Consumer { if (point == null) it.showInFocusCenter() else it.show(point) })
  }

  private fun getPredicate(processor: PsiElementProcessor<T>) = Predicate<ItemWithPresentation> {
    @Suppress("UNCHECKED_CAST") ((it.dereference() as T).let { element -> processor.execute(element) })
  }

  private fun navigate(project: Project,
                       @PopupTitle title: String?,
                       processor: PsiElementProcessor<T>,
                       popupConsumer: Consumer<JBPopup>): Boolean
  {
    val (items, selected) = computeItems(project)
    val predicate = getPredicate(processor)
    if (items.isEmpty()) {
      return false
    }
    else if (items.size == 1 && updater == null) {
      predicate.test(items.first())
    }
    else {
      val popup = buildPopup(items, title, project, selected, predicate)
      popupConsumer.accept(popup)
      updater?.let { ProgressManager.getInstance().run(updater!!) }
    }
    return true
  }

  fun performSilently(project: Project, processor: PsiElementProcessor<T>) {
    val (items) = computeItems(project)
    val predicate = getPredicate(processor)
    if (items.isEmpty()) {
      return
    }
    else {
      predicate.test(items.first())
    }
  }

  private fun computeItems(project: Project): Pair<List<ItemWithPresentation>, ItemWithPresentation?> {
    return ActionUtil.underModalProgress(project, CodeInsightBundle.message("progress.title.preparing.result"),
                                         Computable {
                                           val elements = supplier.get()
                                           elementsConsumer?.accept(elements, this)
                                           val list = elements.map {
                                             ItemWithPresentation(SmartPointerManager.createPointer(it),
                                                                  presentationProvider.getPresentation(it))
                                           }
                                           val selected = if (selection == null) null
                                           else list[elements.indexOf(selection)]
                                           return@Computable Pair<List<ItemWithPresentation>, ItemWithPresentation?>(list, selected)
                                         })
  }

  private fun buildPopup(targets: List<ItemWithPresentation>,
                         @PopupTitle title: String?,
                         project: Project,
                         selected: ItemWithPresentation?,
                         predicate: Predicate<ItemWithPresentation>): JBPopup {
    if (updater == null) {
      require(targets.size > 1) {
        "Attempted to build a target popup with ${targets.size} elements"
      }
    }
    val builder = buildTargetPopupWithMultiSelect(targets, Function { it.presentation }, predicate)
    val caption = title ?: this.title ?: updater?.getCaption(targets.size)
    caption?.let { builder.setTitle(caption) }
    val ref: Ref<UsageView> = Ref.create()
    if (tabTitle != null) {
      builder.setCouldPin {
        val currentItems = updater?.items ?: targets
        ref.set(FindUtil.showInUsageView(null, tabTitle!!, project,
                                         currentItems.map2Array { item -> item.item as SmartPsiElementPointer<*> }))
        !ref.isNull
      }
    }
    selected?.let { builder.setSelectedValue(selected, true) }
    updater?.let {
      builder.setCancelCallback {
        it.cancelTask()
        true
      }
    }
    builderConsumer?.accept(builder)

    val popup = builder.createPopup()
    updater?.init(popup, builder.backgroundUpdater, ref)
    return popup
  }
}

fun interface TargetPresentationProvider<T: PsiElement> {
  fun getPresentation(element: T): TargetPresentation
}

abstract class TargetUpdaterTask(project: Project, @NlsContexts.ProgressTitle title: String):
  BackgroundUpdaterTaskBase<ItemWithPresentation>(project, title, null) {

  override fun createUsage(element: ItemWithPresentation): Usage? {
    return UsageInfo2UsageAdapter(UsageInfo(element.item as SmartPsiElementPointer<*>, null, false, false))
  }

  fun updateComponent(psiElement: PsiElement): Boolean {
    return updateComponent(
      ReadAction.compute<ItemWithPresentation, Throwable> { createItem(psiElement, Function { targetPresentation(psiElement) }) })
  }
}