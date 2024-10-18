// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.lang.manifest.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.lang.manifest.psi.Header;

import java.util.ArrayList;
import java.util.List;

public class ManifestFoldingBuilder extends FoldingBuilderEx implements DumbAware {

  /**
   * The <a href="https://docs.oracle.com/en/java/javase/22/docs/specs/jar/jar.html#notes-on-manifest-and-signature-files">JAR file
   * specification</a> states each line should be no longer than 72 bytes
   */
  private static final int MAX_BYTES_PER_LINE = 72;
  private static final String ELLIPSIS = "...";

  @Override
  public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
    List<FoldingDescriptor> descriptors = new ArrayList<>();

    root.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);

        if (element instanceof Header header) {
          // Prevent collapsing the new lines too
          var textRange = TextRange.from(header.getTextRange().getStartOffset(), getTextLengthWithoutFinalNewline(header));
          descriptors.add(new FoldingDescriptor(element, textRange));
        }
      }
    });

    return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
  }

  @Nullable
  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    if (node.getPsi() instanceof Header) {
      String usefulText = node.getText().trim();
      if (usefulText.length() <= MAX_BYTES_PER_LINE) {
        return node.getText();
      }
      int endIndex = Math.min(MAX_BYTES_PER_LINE - ELLIPSIS.length(), usefulText.length());
      return node.getText().substring(0, endIndex) + ELLIPSIS;
    }

    return null;
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false; // no change in existing behaviour
  }

  private static int getTextLengthWithoutFinalNewline(PsiElement psiElement) {
    int lengthOfTextWithoutFinalNewline = psiElement.getTextLength();
    if (psiElement.getText().endsWith("\n") || psiElement.getText().endsWith("\r")) {
      lengthOfTextWithoutFinalNewline -= 1;
    }
    else if (psiElement.getText().endsWith("\r\n")) {
      lengthOfTextWithoutFinalNewline -= 2;
    }
    return lengthOfTextWithoutFinalNewline;
  }
}
