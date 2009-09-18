/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class CastingLookupElementDecorator extends LookupElementDecorator<LookupElement> implements TypedLookupItem {
  private final LookupElement myCastItem;
  private final PsiType myCastType;

  @Nullable
  private static String getItemText(LookupElementPresentation base, LookupElement castItem) {
    final LookupElementPresentation castPresentation = new LookupElementPresentation(base.isReal());
    castItem.renderElement(castPresentation);
    return castPresentation.getItemText();
  }

  private CastingLookupElementDecorator(LookupElement delegate, PsiType castType) {
    super(delegate);
    myCastType = castType;
    myCastItem = PsiTypeLookupItem.createLookupItem(castType);
  }

  public PsiType getType() {
    return myCastType;
  }

  @Override
  public String toString() {
    return "(" + myCastItem.getLookupString() + ")" + getDelegate().getLookupString();
  }

  public void renderElement(LookupElementPresentation presentation) {
    getDelegate().renderElement(presentation);
    final String castType = getItemText(presentation, getCastItem());
    presentation.setItemText("(" + castType + ")" + presentation.getItemText());
    presentation.setTypeText(castType);
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

  public LookupElement getCastItem() {
    return myCastItem;
  }

  static LookupElement createCastingElement(final LookupElement delegate, PsiType castTo) {
    return new CastingLookupElementDecorator(delegate, castTo);
  }
}
