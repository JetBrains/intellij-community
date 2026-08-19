// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.codeInsight.javadoc.JavaDocInfoMarkdownPrinter;
import com.intellij.codeInsight.javadoc.JavaDocInfoPrinter;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;

/// Markdown variant of the javadoc info generator
public final class JavaDocInfoGeneratorMarkdownTest extends JavaDocInfoGeneratorTest {
  @Override
  protected @NotNull JavaDocInfoPrinter getPrinter() {
    return new JavaDocInfoMarkdownPrinter() {
      @Override
      public @NotNull StringBuilder printLinkURI(@NotNull StringBuilder builder, @NotNull PsiElement targetElement) {
        String refText = JavaDocUtil.getReferenceText(targetElement.getProject(), targetElement);
        if (refText != null) {
          return builder.append(refText
          .replace('(', ' ')
          .replace(')', ' ')
          .replace('[', ' ')
          .replace(']', ' ')
          .replace(' ', '-')
          );
        }
        return builder;
      }
    };
  }

  @Override
  protected @NotNull String getExpectedFileExtension() {
    return ".md";
  }

  @Override
  protected @NotNull String decorate(@NotNull String text) {
    return text;
  }

  @Override
  protected String replaceEnvironmentDependentContent(String html) {
    return html;
  }

  /// @implNote diff from parent: no quick doc check 
  @Override
  protected void doTestEnumConstant() {
        PsiClass psiClass = getTestClass();
        PsiField field = psiClass.getFields()[0];
        String docInfo = generateDocInfo(field);
        assertNotNull(docInfo);
        assertFileTextEquals(docInfo);
  }
}