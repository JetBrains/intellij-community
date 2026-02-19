// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PomTargetUtils")
@file:ApiStatus.Internal

package com.intellij.microservices.utils

import com.intellij.ide.presentation.Presentation
import com.intellij.ide.presentation.PresentationProvider
import com.intellij.pom.PomRenameableTarget
import com.intellij.pom.PomTarget
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.DelegatePsiTarget
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTarget
import com.intellij.util.asSafely
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

@ApiStatus.Internal
fun toPomRenameableTarget(delegate: PomTarget,
                          nameIfNeeded: String,
                          icon: Icon?,
                          @Nls(capitalization = Nls.Capitalization.Title) typeName: String?): PomRenameableTarget<out Any?> {
  if (delegate is PomRenameableTarget<out Any?>) return delegate
  if (delegate is PsiTarget) {
    return object : DelegateSimpleNamePomTarget(delegate, nameIfNeeded, icon, typeName), PsiTarget by delegate {}
  }
  return DelegateSimpleNamePomTarget(delegate, nameIfNeeded, icon, typeName)
}

@ApiStatus.Internal
fun toPomRenameableTarget(delegate: PsiElement,
                          nameIfNeeded: String,
                          icon: Icon?,
                          @Nls(capitalization = Nls.Capitalization.Title)
                          typeName: String?
): PomRenameableTarget<out Any?> {
  if (delegate is PomRenameableTarget<out Any?>) return delegate
  if (delegate is PomTargetPsiElement) {
    delegate.target.asSafely<PomRenameableTarget<out Any?>>()?.let { return it }
    return toPomRenameableTarget(delegate.target, nameIfNeeded, icon, typeName)
  }
  return toPomRenameableTarget(DelegatePsiTarget(delegate), nameIfNeeded, icon ?: delegate.getIcon(0), typeName)
}


@Presentation(provider = DelegateSimpleNamePomTarget.DelegateSimpleNamePomTargetPresentationProvider::class)
private open class DelegateSimpleNamePomTarget(
  private val delegate: PomTarget,
  name: String,
  private val icon: Icon?,
  @Nls(capitalization = Nls.Capitalization.Title)
  private val typeName: String?
) : SimpleNamePomTarget(name) {

  override fun navigate(requestFocus: Boolean) = delegate.navigate(requestFocus)

  override fun canNavigate(): Boolean = delegate.canNavigate()

  override fun canNavigateToSource(): Boolean = delegate.canNavigateToSource()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as DelegateSimpleNamePomTarget

    return delegate == other.delegate
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + delegate.hashCode()
    return result
  }

  class DelegateSimpleNamePomTargetPresentationProvider : PresentationProvider<DelegateSimpleNamePomTarget>() {
    override fun getName(t: DelegateSimpleNamePomTarget): String = t.name

    override fun getIcon(t: DelegateSimpleNamePomTarget): Icon? = t.icon

    override fun getTypeName(t: DelegateSimpleNamePomTarget): String? = t.typeName
  }
}