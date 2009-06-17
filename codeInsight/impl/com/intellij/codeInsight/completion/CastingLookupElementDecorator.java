/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.DecoratingLookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.MemorizingLookupElementPresentation;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class CastingLookupElementDecorator extends LookupElementDecorator<LookupElement> implements TypedLookupItem {
  private final LookupItem myCastItem;
  private final PsiType myCastType;

  public CastingLookupElementDecorator(LookupElement delegate, PsiType castType) {
    super(delegate);
    myCastType = castType;
    myCastItem = PsiTypeLookupItem.createLookupItem(castType);
  }

  public PsiType getType() {
    return myCastType;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    final MemorizingLookupElementPresentation castPresentation = new MemorizingLookupElementPresentation(presentation);
    myCastItem.renderElement(castPresentation);


    super.renderElement(new DecoratingLookupElementPresentation(presentation) {
      @Override
      public void setItemText(@Nullable String text) {
        super.setItemText("(" + castPresentation.getItemText() + ")" + text);
      }

      @Override
      public void setItemText(@Nullable String text, boolean strikeout, boolean bold) {
        super.setItemText("(" + castPresentation.getItemText() + ")" + text, strikeout, bold);
      }
    });

    presentation.setTypeText(myCastItem.getLookupString());
  }

  @Override
  public void handleInsert(InsertionContext context) {
    final OffsetKey castInsertion = OffsetKey.create("castInsertion", false);
    context.getOffsetMap().addOffset(castInsertion, context.getStartOffset());
    super.handleInsert(context);

    final int newStart = context.getOffsetMap().getOffset(castInsertion);
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(context.getProject());
    String spaceWithin = settings.SPACE_WITHIN_CAST_PARENTHESES ? " " : "";
    String spaceAfter = settings.SPACE_AFTER_TYPE_CAST ? " " : "";
    context.getEditor().getDocument().insertString(newStart, "(" + spaceWithin + spaceWithin + ")" + spaceAfter);
    emulateInsertion(context, ("(" + spaceWithin).length() + newStart, myCastItem);
  }

  private static InsertionContext emulateInsertion(InsertionContext oldContext, int newStart, final LookupElement item) {
    final Document document = oldContext.getEditor().getDocument();
    final OffsetMap newMap = new OffsetMap(document);
    newMap.addOffset(CompletionInitializationContext.START_OFFSET, newStart);
    newMap.addOffset(InsertionContext.TAIL_OFFSET, newStart);
    newMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, newStart);
    document.insertString(newStart, item.getLookupString());
    final InsertionContext newContext = new InsertionContext(newMap, (char)0, EMPTY_ARRAY, oldContext.getFile(), oldContext.getEditor());
    item.handleInsert(newContext);
    return newContext;
  }
}
