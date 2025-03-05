package com.intellij.microservices.utils

import com.intellij.find.actions.ShowUsagesAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.pom.PomRenameableTarget
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.PomTargetPsiElementImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
open class CommonFakeNavigatablePomTarget(project: Project, pomTarget: PomRenameableTarget<out Any?>)
  : PomTargetPsiElementImpl(project, pomTarget) {

  override fun navigate(requestFocus: Boolean) {
    showFindUsages()
  }

  protected fun showFindUsages() {
    if (mockedFindUsages != null) return mockedFindUsages!!.invoke(this)
    // mb make GotoDeclarationAction.startFindUsages public and use it here instead?
    if (DumbService.getInstance(project).isDumb) return
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
    val popupPosition = JBPopupFactory.getInstance().guessBestPopupLocation(editor)
    ShowUsagesAction.startFindUsages(this, popupPosition, editor)
  }

  override fun canNavigate(): Boolean = true
  override fun toString(): String = "${this.javaClass.simpleName}(${this.target})"
  override fun isEquivalentTo(another: PsiElement?): Boolean =
    super.isEquivalentTo(another) || (another is PomTargetPsiElement && another.target == target)
}

private var mockedFindUsages: ((CommonFakeNavigatablePomTarget) -> Unit)? = null

@TestOnly
fun mockFindUsages(disposable: Disposable, handler: (CommonFakeNavigatablePomTarget) -> Unit) {
  val oldValue = mockedFindUsages
  mockedFindUsages = handler
  Disposer.register(disposable, Disposable {
    mockedFindUsages = oldValue
  })
}