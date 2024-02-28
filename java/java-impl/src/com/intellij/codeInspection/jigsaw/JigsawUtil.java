// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.jigsaw;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.lang.jvm.JvmMethod;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.light.LightJavaModule;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public final class JigsawUtil {
  private JigsawUtil() { }

  public static void addProviderMethod(@NotNull PsiClass targetClass,
                                       @NotNull Editor editor,
                                       int startOffset,
                                       @NotNull BiConsumer<Integer, String> setMethod) {
    String className = targetClass.getName();
    if (className == null) return;

    String methodStringBeforeCursor = "public static " + className + " " + JigsawApiConstants.PROVIDER + "() {" +
                                      "return new " + className + "(";
    String methodStringAfterCursor = ");}";

    Document document = editor.getDocument();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(targetClass.getProject());

    setMethod.accept(startOffset, methodStringBeforeCursor + methodStringAfterCursor);

    editor.getCaretModel().moveToOffset(startOffset + methodStringBeforeCursor.length());
    documentManager.commitDocument(document);
    documentManager.doPostponedOperationsAndUnblockDocument(document);

    PsiFile psiFile = documentManager.getPsiFile(document);
    if (psiFile != null) {
      CodeStyleManager.getInstance(targetClass.getProject())
        .reformatText(psiFile, startOffset, startOffset + methodStringBeforeCursor.length() + methodStringAfterCursor.length() + 1);
    }
  }

  @Contract("null -> false")
  public static boolean checkProviderMethodAccessible(@Nullable PsiClass targetClass) {
    if (targetClass == null || targetClass.getName() == null) return false;
    JvmMethod[] methods = targetClass.findMethodsByName(JigsawApiConstants.PROVIDER);
    for (JvmMethod method : methods) {
      if (!method.hasParameters()) return false;
    }
    PsiJavaModule descriptor = JavaModuleGraphUtil.findDescriptorByElement(targetClass.getContainingFile().getOriginalFile());
    if (descriptor == null || descriptor instanceof LightJavaModule) return false;

    Iterable<PsiProvidesStatement> providers = descriptor.getProvides();
    for (PsiProvidesStatement provider : providers) {
      PsiReferenceList implementations = provider.getImplementationList();
      if (implementations == null) continue;
      for (PsiClassType type : implementations.getReferencedTypes()) {
        if (targetClass.isEquivalentTo(type.resolve())) {
          return true;
        }
      }
    }
    return false;
  }
}
