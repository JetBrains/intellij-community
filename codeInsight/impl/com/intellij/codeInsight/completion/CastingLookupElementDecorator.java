/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.DecoratingLookupElementPresentation;
import com.intellij.codeInsight.lookup.MemorizingLookupElementPresentation;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.openapi.editor.Editor;
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
  public String toString() {
    return "(" + myCastItem.getLookupString() + ")" + getDelegate().getLookupString();
  }

  @Override
  public void handleInsert(InsertionContext context) {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(context.getProject());
    String spaceWithin = settings.SPACE_WITHIN_CAST_PARENTHESES ? " " : "";
    String spaceAfter = settings.SPACE_AFTER_TYPE_CAST ? " " : "";
    final Editor editor = context.getEditor();
    editor.getDocument().replaceString(context.getStartOffset(), context.getTailOffset(), "(" + spaceWithin + spaceWithin + ")" + spaceAfter);
    CompletionUtil.emulateInsertion(context, context.getStartOffset() + 1 + spaceWithin.length(), myCastItem, (char) 0);

    CompletionUtil.emulateInsertion(getDelegate(), context.getTailOffset(), context);
  }

}
