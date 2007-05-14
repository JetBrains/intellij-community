/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.completion.simple;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.DefaultInsertHandler;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.LookupData;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.ide.IconUtilEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author peter
 */
public class SimpleLookupItem extends LookupItem {
  private static final OverwriteHandler DEFAULT_OVERWRITE_HANDLER = new OverwriteHandler() {
    public void handleOverwrite(final Editor editor) {
      final int offset = editor.getCaretModel().getOffset();
      final Document document = editor.getDocument();
      final CharSequence sequence = document.getCharsSequence();
      int i = offset;
      while (i < sequence.length() && Character.isJavaIdentifierPart(sequence.charAt(i))) i++;
      document.deleteString(offset, i);
      PsiDocumentManager.getInstance(editor.getProject()).commitDocument(document);
    }
  };
  public static final SimpleLookupItem[] EMPTY_ARRAY = new SimpleLookupItem[0];
  @NotNull private OverwriteHandler myOverwriteHandler = DEFAULT_OVERWRITE_HANDLER;

  public SimpleLookupItem(@NotNull @NonNls final String lookupString) {
    super(lookupString, lookupString);
  }

  public SimpleLookupItem(@NotNull final PsiNamedElement element) {
    this(element, StringUtil.notNullize(element.getName()));
  }

  public SimpleLookupItem(@NotNull final PsiElement element, @NotNull @NonNls final String lookupString) {
    super(element, lookupString);
    setIcon(IconUtilEx.getIcon(element, 0, element.getProject()));
    final SimpleInsertHandler handler = SimpleInsertHandlerFactory.createInsertHandler(element);
    if (handler != null) {
      setInsertHandler(handler);
    }
  }

  public SimpleLookupItem setInsertHandler(@NotNull final SimpleInsertHandler handler) {
    setAttribute(LookupItem.INSERT_HANDLER_ATTR, new MyInsertHandler(handler));
    return this;
  }

  public SimpleLookupItem setOverwriteHandler(@NotNull final OverwriteHandler overwriteHandler) {
    myOverwriteHandler = overwriteHandler;
    return this;
  }

  public SimpleLookupItem setTailType(@NotNull TailType type) {
    super.setTailType(type);
    if (getInsertHandler() == null) {
      setInsertHandler(SimpleInsertHandler.EMPTY_HANDLER);
    }
    return this;
  }

  public SimpleLookupItem setBold() {
    setAttribute(LookupItem.HIGHLIGHTED_ATTR, "");
    return this;
  }

  public SimpleLookupItem setIcon(Icon icon) {
    setAttribute(LookupItem.ICON_ATTR, icon);
    return this;
  }

  public SimpleLookupItem setTypeText(final String text) {
    setAttribute(LookupItem.TYPE_TEXT_ATTR, text);
    return this;
  }

  public SimpleLookupItem setCaseInsensitive(final boolean caseInsensitive) {
    setAttribute(LookupItem.CASE_INSENSITIVE, caseInsensitive);
    return this;
  }


  private class MyInsertHandler implements InsertHandler {
    private final SimpleInsertHandler myHandler;

    public MyInsertHandler(final SimpleInsertHandler handler) {
      myHandler = handler;
    }

    public void handleInsert(final CompletionContext context,
                             final int startOffset, final LookupData data, final LookupItem item,
                             final boolean signatureSelected, final char completionChar) {
      final Editor editor = context.editor;
      if (completionChar == Lookup.REPLACE_SELECT_CHAR) {
        myOverwriteHandler.handleOverwrite(editor);
      }
      final TailType tailType = DefaultInsertHandler.getTailType(completionChar, item);
      final int tailOffset = myHandler.handleInsert(editor, startOffset, SimpleLookupItem.this, data.items, tailType);
      tailType.processTail(editor, tailOffset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
    }
  }
}
