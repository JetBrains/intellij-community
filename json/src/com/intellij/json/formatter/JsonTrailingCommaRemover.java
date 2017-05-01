/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.json.formatter;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.impl.JsonRecursiveElementVisitor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class JsonTrailingCommaRemover implements PreFormatProcessor {

  @NotNull
  @Override
  public TextRange process(@NotNull ASTNode element, @NotNull TextRange range) {
    PsiElement rootPsi = element.getPsi();
    if (rootPsi.getLanguage() != JsonLanguage.INSTANCE) {
      return range;
    }
    JsonCodeStyleSettings settings = CodeStyleSettingsManager.getInstance(rootPsi.getProject())
      .getCurrentSettings()
      .getCustomSettings(JsonCodeStyleSettings.class);
    if (settings.KEEP_TRAILING_COMMA) {
      return range;
    }
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(rootPsi.getProject());
    Document document = psiDocumentManager.getDocument(rootPsi.getContainingFile());
    if (document == null) {
      return range;
    }
    DocumentUtil.executeInBulk(document, true, () -> {
      psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
      PsiElementVisitor visitor = new Visitor(document);
      rootPsi.accept(visitor);
      psiDocumentManager.commitDocument(document);
    });
    return range;
  }

  private static class Visitor extends JsonRecursiveElementVisitor {
    private final Document myDocument;
    private int myOffsetDelta;

    public Visitor(Document document) {
      myDocument = document;
    }

    @Override
    public void visitArray(@NotNull JsonArray o) {
      super.visitArray(o);
      collectTrailingCommas(o).forEach(this::deleteNode);
    }

    @Override
    public void visitObject(@NotNull JsonObject o) {
      super.visitObject(o);
      collectTrailingCommas(o).forEach(this::deleteNode);
    }

    @NotNull
    private static Collection<ASTNode> collectTrailingCommas(@NotNull PsiElement element) {
      List<ASTNode> result = ContainerUtil.newArrayList();
      element = element.getLastChild();
      while (element != null) {
        element = element.getPrevSibling();
        if (element != null && element.getNode().getElementType() == JsonElementTypes.COMMA
            || (element instanceof PsiErrorElement && ",".equals(element.getText()))) {
          result.add(element.getNode());
        }
        else if (!(element instanceof PsiComment || element instanceof PsiWhiteSpace)) {
          break;
        }
      }
      return ContainerUtil.reverse(result);
    }

    private void deleteNode(@NotNull ASTNode node) {
      int length = node.getTextLength();
      myDocument.deleteString(node.getStartOffset() + myOffsetDelta, node.getStartOffset() + length + myOffsetDelta);
      myOffsetDelta -= length;
    }
  }
}
