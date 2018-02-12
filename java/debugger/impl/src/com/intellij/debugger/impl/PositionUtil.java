/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.impl;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PositionUtil extends ContextUtil {
  public static SourcePosition getSourcePosition(final StackFrameContext context) {
    if(context instanceof DebuggerContextImpl) return ((DebuggerContextImpl)context).getSourcePosition();

    return ContextUtil.getSourcePosition(context);
  }

  @Nullable
  public static PsiElement getContextElement(final StackFrameContext context) {
    if(context instanceof DebuggerContextImpl) return ((DebuggerContextImpl) context).getContextElement();

    return ContextUtil.getContextElement(context);
  }

  @Nullable
  public static <T extends PsiElement> T getPsiElementAt(@NotNull Project project,
                                                         @NotNull Class<T> expectedPsiElementClass,
                                                         @Nullable SourcePosition sourcePosition) {
    if (sourcePosition == null) {
      return null;
    }
    return ReadAction.compute(() -> {
      final PsiFile psiFile = sourcePosition.getFile();
      final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
      if(document == null) {
        return null;
      }
      final int spOffset = sourcePosition.getOffset();
      if (spOffset < 0) {
        return null;
      }
      final int offset = CharArrayUtil.shiftForward(document.getCharsSequence(), spOffset, " \t");
      return PsiTreeUtil.getParentOfType(psiFile.findElementAt(offset), expectedPsiElementClass, false);
    });
  }
}
