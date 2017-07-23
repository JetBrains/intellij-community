/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.json.codeinsight;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class JsonStringPropertyInsertHandler implements InsertHandler<LookupElement> {

  private final String myNewValue;

  public JsonStringPropertyInsertHandler(@NotNull String newValue) {
    myNewValue = newValue;
  }

  @Override
  public void handleInsert(InsertionContext context, LookupElement item) {
    PsiElement element = context.getFile().findElementAt(context.getStartOffset());
    JsonStringLiteral literal = PsiTreeUtil.getParentOfType(element, JsonStringLiteral.class, false);
    if (literal == null) return;
    JsonProperty property = ObjectUtils.tryCast(literal.getParent(), JsonProperty.class);
    if (property == null) return;
    final TextRange toDelete;
    String textToInsert = "";
    TextRange literalRange = literal.getTextRange();
    if (literal.getValue().equals(myNewValue)) {
      toDelete = new TextRange(literalRange.getEndOffset(), literalRange.getEndOffset());
    }
    else {
      toDelete = literalRange;
      textToInsert = StringUtil.wrapWithDoubleQuote(myNewValue);
    }
    int newCaretOffset = literalRange.getStartOffset() + 1 + myNewValue.length();
    boolean showAutoPopup = false;
    if (property.getNameElement().equals(literal)) {
      if (property.getValue() == null) {
        textToInsert += ":\"\"";
        newCaretOffset += 3; // "package<caret offset>":"<new caret offset>"
        if (needCommaAfter(property)) {
          textToInsert += ",";
        }
        showAutoPopup = true;
      }
    }
    context.getDocument().replaceString(toDelete.getStartOffset(), toDelete.getEndOffset(), textToInsert);
    context.getEditor().getCaretModel().moveToOffset(newCaretOffset);
    reformat(context, toDelete.getStartOffset(), toDelete.getStartOffset() + textToInsert.length());
    if (showAutoPopup) {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    }
  }

  private static boolean needCommaAfter(@NotNull JsonProperty property) {
    PsiElement element = property.getNextSibling();
    while (element != null) {
      if (element instanceof JsonProperty) {
        return true;
      }
      if (element.getNode().getElementType() == JsonElementTypes.COMMA) {
        return false;
      }
      element = element.getNextSibling();
    }
    return false;
  }

  private static void reformat(@NotNull InsertionContext context, int startOffset, int endOffset) {
    PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getDocument());
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(context.getProject());
    codeStyleManager.reformatText(context.getFile(), startOffset, endOffset);
  }
}
