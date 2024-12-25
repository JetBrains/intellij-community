package com.intellij.microservices.mime

import com.intellij.find.actions.ShowUsagesAction
import com.intellij.icons.AllIcons
import com.intellij.microservices.MicroservicesBundle
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntheticElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.meta.PsiMetaData
import com.intellij.psi.meta.PsiMetaOwner
import com.intellij.psi.meta.PsiPresentableMetaData
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import javax.swing.Icon

internal class MimeTypePsiElement(private val parent: PsiElement,
                                  private val mimeTypeName: String) : FakePsiElement(), SyntheticElement, PsiMetaOwner, PsiPresentableMetaData {
  override fun getName(): String {
    return mimeTypeName
  }

  override fun getParent(): PsiElement {
    return parent
  }

  override fun getName(context: PsiElement): String {
    return name
  }

  override fun getDeclaration(): PsiElement {
    return this
  }

  override fun getUseScope(): SearchScope {
    return GlobalSearchScope.allScope(project)
  }

  override fun getResolveScope(): GlobalSearchScope {
    return GlobalSearchScope.allScope(project)
  }

  override fun init(element: PsiElement) {}

  override fun getIcon(): Icon = AllIcons.Nodes.Type

  override fun getMetaData(): PsiMetaData = this

  override fun getNavigationElement(): PsiElement = this

  override fun getTypeName(): String {
    return MicroservicesBundle.message("mime.type.element")
  }

  override fun isEquivalentTo(another: PsiElement?): Boolean {
    return another is MimeTypePsiElement && another.mimeTypeName == mimeTypeName
  }

  override fun canNavigate(): Boolean {
    return true
  }

  override fun navigate(requestFocus: Boolean) {
    if (DumbService.getInstance(project).isDumb) return
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
    val popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(editor)
    ShowUsagesAction.startFindUsages(this, popupPosition, editor)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MimeTypePsiElement

    return mimeTypeName == other.mimeTypeName
  }

  override fun hashCode(): Int {
    return mimeTypeName.hashCode()
  }
}