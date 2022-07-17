// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.codeInsight.intention.impl.QuickEditHandler;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.InjectedFileChangesHandler;
import com.intellij.injected.editor.InjectedFileChangesHandlerProvider;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.injected.changesHandler.BaseInjectedFileChangesHandler;
import com.intellij.psi.javadoc.PsiSnippetDocTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaInjectedFileChangesHandlerProvider implements InjectedFileChangesHandlerProvider {

  @Override
  public InjectedFileChangesHandler createFileChangesHandler(List<PsiLanguageInjectionHost.Shred> shreds,
                                                             Editor hostEditor,
                                                             Document newDocument,
                                                             PsiFile injectedFile) {
    if (Registry.is("injections.java.fragment.editor.new") && !hasBlockLiterals(shreds) && !hasSnippet(shreds)) {
      return new JavaInjectedFileChangesHandler(shreds, hostEditor, newDocument, injectedFile);
    }
    else {
      return new OldJavaInjectedFileChangesHandler(shreds, hostEditor, newDocument, injectedFile);
    }
  }

  private static boolean hasSnippet(List<PsiLanguageInjectionHost.Shred> shreds) {
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      if (shred.getHost() instanceof PsiSnippetDocTag) return true;
    }
    return false;
  }

  private static boolean hasBlockLiterals(List<PsiLanguageInjectionHost.Shred> shreds) {
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      PsiLanguageInjectionHost host = shred.getHost();
      if (!(host instanceof PsiLiteralExpression)) continue;
      if (((PsiLiteralExpression)host).isTextBlock()) return true;
    }
    return false;
  }
}

class OldJavaInjectedFileChangesHandler extends BaseInjectedFileChangesHandler {

  @NotNull
  private final RangeMarker myAltFullRange;

  OldJavaInjectedFileChangesHandler(List<PsiLanguageInjectionHost.Shred> shreds, Editor editor,
                                    Document newDocument,
                                    PsiFile injectedFile) {
    super(editor, newDocument, injectedFile);

    PsiLanguageInjectionHost.Shred firstShred = ContainerUtil.getFirstItem(shreds);
    PsiLanguageInjectionHost.Shred lastShred = ContainerUtil.getLastItem(shreds);
    myAltFullRange = myHostDocument.createRangeMarker(
      firstShred.getHostRangeMarker().getStartOffset(),
      lastShred.getHostRangeMarker().getEndOffset());
    myAltFullRange.setGreedyToLeft(true);
    myAltFullRange.setGreedyToRight(true);
  }

  @Override
  public boolean isValid() {
    return myAltFullRange.isValid();
  }

  @Override
  public void commitToOriginal(@NotNull DocumentEvent e) {
    final PsiFile origPsiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myHostDocument);
    String newText = myFragmentDocument.getText();
    // prepare guarded blocks
    LinkedHashMap<String, String> replacementMap = new LinkedHashMap<>();
    int count = 0;
    for (RangeMarker o : ContainerUtil.reverse(((DocumentEx)myFragmentDocument).getGuardedBlocks())) {
      String replacement = o.getUserData(QuickEditHandler.REPLACEMENT_KEY);
      String tempText = "REPLACE" + (count++) + Long.toHexString(StringHash.calc(replacement));
      newText = newText.substring(0, o.getStartOffset()) + tempText + newText.substring(o.getEndOffset());
      replacementMap.put(tempText, replacement);
    }
    // run preformat processors
    final int hostStartOffset = myAltFullRange.getStartOffset();
    myHostEditor.getCaretModel().moveToOffset(hostStartOffset);
    for (CopyPastePreProcessor preProcessor : CopyPastePreProcessor.EP_NAME.getExtensionList()) {
      newText = preProcessor.preprocessOnPaste(myProject, origPsiFile, myHostEditor, newText, null);
    }
    myHostDocument.replaceString(hostStartOffset, myAltFullRange.getEndOffset(), newText);
    // replace temp strings for guarded blocks
    for (String tempText : replacementMap.keySet()) {
      int idx = CharArrayUtil.indexOf(myHostDocument.getCharsSequence(), tempText, hostStartOffset, myAltFullRange.getEndOffset());
      myHostDocument.replaceString(idx, idx + tempText.length(), replacementMap.get(tempText));
    }
    // JAVA: fix occasional char literal concatenation
    fixDocumentQuotes(myHostDocument, hostStartOffset - 1);
    fixDocumentQuotes(myHostDocument, myAltFullRange.getEndOffset());

    // reformat
    PsiDocumentManager.getInstance(myProject).commitDocument(myHostDocument);
    try {
      CodeStyleManager.getInstance(myProject).reformatRange(
        origPsiFile, hostStartOffset, myAltFullRange.getEndOffset(), true);
    }
    catch (IncorrectOperationException e1) {
      //LOG.error(e);
    }

    PsiElement newInjected = InjectedLanguageManager.getInstance(myProject).findInjectedElementAt(origPsiFile, hostStartOffset);
    DocumentWindow documentWindow = newInjected == null ? null : InjectedLanguageUtil.getDocumentWindow(newInjected);
    if (documentWindow != null) {
      myHostEditor.getCaretModel().moveToOffset(documentWindow.injectedToHost(e.getOffset()));
      myHostEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
  }

  @Override
  public boolean tryReuse(@NotNull PsiFile newInjectedFile, @NotNull TextRange newHostRange) {
    DocumentWindow documentWindow = InjectedLanguageUtil.getDocumentWindow(newInjectedFile);
    if (documentWindow == null || documentWindow.getDelegate() != myAltFullRange.getDocument()) {
      return false;
    }

    return super.tryReuse(newInjectedFile, newHostRange);
  }

  @Override
  public boolean handlesRange(@NotNull TextRange hostRange) {
    return hostRange.intersects(myAltFullRange.getStartOffset(), myAltFullRange.getEndOffset());
  }

  private static void fixDocumentQuotes(Document doc, int offset) {
    if (doc.getCharsSequence().charAt(offset) == '\'') {
      doc.replaceString(offset, offset + 1, "\"");
    }
  }
}
