// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.changeSignature;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.inplace.LanguageChangeSignatureDetector;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaChangeSignatureDetector implements LanguageChangeSignatureDetector<DetectedJavaChangeInfo> {

  @NotNull
  @Override
  public DetectedJavaChangeInfo createInitialChangeInfo(final @NotNull PsiElement element) {
    return DetectedJavaChangeInfo.createFromMethod(PsiTreeUtil.getParentOfType(element, PsiMethod.class), false);
  }

  @Override
  public void performChange(final DetectedJavaChangeInfo changeInfo, Editor editor, @NotNull final String oldText) {
    changeInfo.perform(oldText, editor, true);
  }

  @Override
  public boolean isChangeSignatureAvailableOnElement(@NotNull PsiElement element, DetectedJavaChangeInfo currentInfo) {
    final PsiMethod method = currentInfo.getMethod();
    TextRange range = method.getTextRange();
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      range = new TextRange(range.getStartOffset(), body.getTextOffset());
    }
    return element.getContainingFile() == method.getContainingFile() && range.contains(element.getTextRange());
  }

  @Override
  public boolean ignoreChanges(PsiElement element) {
    if (element instanceof PsiMethod) return true;
    return PsiTreeUtil.getParentOfType(element, PsiImportList.class) != null;
  }

  @Override
  public @NotNull TextRange getHighlightingRange(@NotNull DetectedJavaChangeInfo changeInfo) {
    PsiMethod method = changeInfo.getMethod();
    return getSignatureRange(method);
  }

  @Override
  public String getMethodSignaturePreview(DetectedJavaChangeInfo initialChangeInfo,
                                          final List<? super TextRange> deleteRanges,
                                          final List<? super TextRange> newRanges) {
    StringBuilder buf = new StringBuilder();
    String visibility = VisibilityUtil.getVisibilityString(initialChangeInfo.getNewVisibility());
    buf.append(visibility);
    if (!StringUtil.isEmptyOrSpaces(visibility)) {
      buf.append(" ");
    }
    CanonicalTypes.Type returnType = initialChangeInfo.getNewReturnType();
    if (returnType != null) {
      buf.append(returnType.getTypeText()).append(" ");
    }
    buf.append(initialChangeInfo.getNewName()).append("(");

    JavaParameterInfo[] newParameters = initialChangeInfo.getNewParameters();
    boolean first = true;
    boolean[] toRemove = initialChangeInfo.toRemoveParm();
    for (int i = 0; i < toRemove.length; i++) {
      if (first) {
        first = false;
      }
      else {
        buf.append(", ");
      }

      String oldParamName = initialChangeInfo.getOldParameterNames()[i];
      String oldParamType = initialChangeInfo.getOldParameterTypes()[i];
      if (toRemove[i]) {
        String deletedParam = oldParamType + " " + oldParamName;
        deleteRanges.add(new TextRange(buf.length(), buf.length() + deletedParam.length()));
        buf.append(deletedParam);
      }
      else {
        for (JavaParameterInfo parameter : newParameters) {
          if (parameter.getOldIndex() == i) {
            buf.append(parameter.getTypeText());
            buf.append(" ");
            if (!oldParamName.equals(parameter.getName())) {
              deleteRanges.add(new TextRange(buf.length(), buf.length() + oldParamName.length()));
              buf.append(oldParamName);
            }
            buf.append(parameter.getName());
            break;
          }
        }
      }
    }

    for (JavaParameterInfo param : newParameters) {
      if (param.isNew()) {
        if (first) {
          first = false;
        }
        else {
          buf.append(", ");
        }
        String paramPresentation = param.getTypeText() + " " + ObjectUtils.notNull(param.getName(), "");
        newRanges.add(new TextRange(buf.length(), buf.length() + paramPresentation.length()));
        buf.append(paramPresentation);
      }
    }
    buf.append(")");
    return buf.toString();
  }

  @Override
  public FileType getFileType() {
    return JavaFileType.INSTANCE;
  }

  @Override
  public DetectedJavaChangeInfo createNextChangeInfo(String signature, @NotNull final DetectedJavaChangeInfo currentInfo, boolean delegate) {
    final PsiElement currentInfoMethod = currentInfo.getMethod();
    final Project project = currentInfoMethod.getProject();

    final PsiMethod oldMethod = currentInfo.getMethod();
    String visibility = "";
    PsiClass containingClass = oldMethod.getContainingClass();
    if (containingClass != null && containingClass.isInterface()) {
      visibility = PsiModifier.PUBLIC + " ";
    }
    PsiMethod method = JavaPsiFacade.getElementFactory(project).createMethodFromText((visibility + signature).trim(), oldMethod);
    return currentInfo.createNextInfo(method, delegate);
  }

  public static @NotNull TextRange getSignatureRange(@NotNull PsiMethod method) {
    int endOffset = method.getThrowsList().getTextRange().getEndOffset();
    int startOffset = method.getTextRange().getStartOffset();
    return new TextRange(startOffset, endOffset);
  }
}
