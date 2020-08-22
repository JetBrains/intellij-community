// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.scope;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public final class JavaScopeProcessorEvent implements PsiScopeProcessor.Event {
  private JavaScopeProcessorEvent() {
  }

  public static final JavaScopeProcessorEvent START_STATIC = new JavaScopeProcessorEvent();

  /**
   * An event issued by {@link com.intellij.psi.scope.util.PsiScopesUtil#treeWalkUp}
   * after {@link PsiElement#processDeclarations} was called,
   * for each element in the hierarchy defined by a chain of {@link PsiElement#getContext()} calls.
   * The associated object is the {@link PsiElement} whose declarations have been processed.
   */
  public static final JavaScopeProcessorEvent EXIT_LEVEL = new JavaScopeProcessorEvent();

  public static final JavaScopeProcessorEvent CHANGE_LEVEL = new JavaScopeProcessorEvent();
  public static final JavaScopeProcessorEvent SET_CURRENT_FILE_CONTEXT = new JavaScopeProcessorEvent();

  public static boolean isEnteringStaticScope(@NotNull PsiScopeProcessor.Event event, @Nullable Object associated) {
    if (event == START_STATIC) return true;

    return event == EXIT_LEVEL &&
           associated instanceof PsiModifierListOwner &&
           ((PsiModifierListOwner)associated).hasModifierProperty(PsiModifier.STATIC);
  }
}
