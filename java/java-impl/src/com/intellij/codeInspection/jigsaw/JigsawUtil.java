// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.jigsaw;

import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.lang.jvm.JvmMethod;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiProvidesStatement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.light.LightJavaModule;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JigsawUtil {
  private JigsawUtil() { }

  /**
   * Adds a provider static method to a target class (java service) at the specified start offset in the navigator.
   * The provider method returns the target class instance.
   *
   * @param targetClass The class to which the provider method will be added.
   * @param navigator   The navigator in which the changes will be made.
   * @param startOffset The start offset in the navigator where the provider method will be inserted.
   */
  public static void addProviderMethod(@NotNull PsiClass targetClass, @NotNull ModNavigator navigator, int startOffset) {
    String className = targetClass.getName();
    if (className == null) return;

    String methodStringBeforeCursor = "public static " + className + " " + PsiJavaModule.PROVIDER_METHOD + "() {" +
                                      "return new " + className + "(";
    String methodStringAfterCursor = ");}";

    Document document = navigator.getDocument();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(targetClass.getProject());

    document.insertString(startOffset, methodStringBeforeCursor + methodStringAfterCursor);

    navigator.moveCaretTo(startOffset + methodStringBeforeCursor.length());
    documentManager.commitDocument(document);

    PsiFile psiFile = navigator.getPsiFile();
    CodeStyleManager.getInstance(targetClass.getProject())
      .reformatText(psiFile, startOffset, startOffset + methodStringBeforeCursor.length() + methodStringAfterCursor.length() + 1);
  }

  /**
   * Verifies the accessibility of the provider method for a specified class. The target class must meet the following criteria:
   * - It implements an interface that is listed in the module-info provider section.
   * - It is declared in the module-info provider section.
   * - It does not have a provider method.
   *
   * @param targetClass The class to be checked for provider method accessibility.
   * @return true if the provider method is accessible, false otherwise.
   */
  @Contract("null -> false")
  public static boolean checkProviderMethodAccessible(@Nullable PsiClass targetClass) {
    if (targetClass == null || targetClass.getName() == null) return false;
    JvmMethod[] methods = targetClass.findMethodsByName(PsiJavaModule.PROVIDER_METHOD);
    for (JvmMethod method : methods) {
      if (!method.hasParameters()) return false;
    }
    PsiJavaModule descriptor = JavaPsiModuleUtil.findDescriptorByElement(targetClass.getContainingFile().getOriginalFile());
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
