// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.utils

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.codeInsight.navigation.NavigationGutterIconRenderer
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.NotNullFunction
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseEvent
import javax.swing.Icon

@ApiStatus.Experimental
class UsagesNavigationGutterIconBuilder<T>(icon: Icon,
                                           converter: NotNullFunction<T, Collection<PsiElement>>,
                                           gotoRelatedItemProvider: NotNullFunction<T, Collection<GotoRelatedItem>>?)
  : NavigationGutterIconBuilder<T>(icon, converter, gotoRelatedItemProvider) {

  init {
    setAlignment(GutterIconRenderer.Alignment.LEFT)
  }

  companion object {
    fun <T> create(icon: Icon, converter: NotNullFunction<T, Collection<PsiElement>>): UsagesNavigationGutterIconBuilder<T> {
      return create(icon, converter, null)
    }

    fun <T> create(icon: Icon, converter: NotNullFunction<T, Collection<PsiElement>>,
                   gotoRelatedItemProvider: NotNullFunction<T, Collection<GotoRelatedItem>>?): UsagesNavigationGutterIconBuilder<T> {
      return UsagesNavigationGutterIconBuilder(icon, converter, gotoRelatedItemProvider)
    }
  }

  override fun createGutterIconRenderer(pointers: NotNullLazyValue<out List<SmartPsiElementPointer<*>>>,
                                        renderer: Computable<out PsiElementListCellRenderer<*>>,
                                        empty: Boolean,
                                        navigationHandler: GutterIconNavigationHandler<PsiElement>?): NavigationGutterIconRenderer {
    return UsagesNavigationGutterIconRenderer(this, myAlignment, myIcon, myTooltipText, pointers, renderer, empty)
  }

  private class UsagesNavigationGutterIconRenderer(builder: UsagesNavigationGutterIconBuilder<*>,
                                                   private val myAlignment: Alignment,
                                                   private val myIcon: Icon,
                                                   @NlsContexts.Tooltip private val myTooltipText: String?,
                                                   pointers: NotNullLazyValue<out List<SmartPsiElementPointer<*>>>,
                                                   cellRenderer: Computable<out PsiElementListCellRenderer<*>>,
                                                   private val myEmpty: Boolean)
    : NavigationGutterIconRenderer(builder.myPopupTitle, builder.myEmptyText, cellRenderer, pointers, true,
                                   GutterIconNavigationHandler(::showUsages)) {

    override fun isNavigateAction(): Boolean {
      return !myEmpty
    }

    override fun getIcon(): Icon {
      return myIcon
    }

    override fun getTooltipText(): String? {
      return myTooltipText
    }

    override fun getAlignment(): Alignment {
      return myAlignment
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (!super.equals(other)) return false
      val that = other as UsagesNavigationGutterIconRenderer
      if (myAlignment != that.myAlignment) return false
      if (myIcon != that.myIcon) return false
      return myTooltipText == that.myTooltipText
    }

    override fun hashCode(): Int {
      var result = super.hashCode()
      result = 31 * result + myAlignment.hashCode()
      result = 31 * result + myIcon.hashCode()
      result = 31 * result + (myTooltipText?.hashCode() ?: 0)
      return result
    }
  }
}

private fun showUsages(event: MouseEvent?, element: PsiElement) {
  if (DumbService.getInstance(element.project).isDumb) return
  val editor = FileEditorManager.getInstance(element.project).selectedTextEditor ?: return

  val popupPosition = if (event == null)
    JBPopupFactory.getInstance().guessBestPopupLocation(editor)
  else
    RelativePoint(event)

  ShowUsagesAction.startFindUsages(element, popupPosition, editor)
}