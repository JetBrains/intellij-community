// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFragment;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.injected.changesHandler.BaseInjectedFileChangesHandler;
import com.intellij.psi.javadoc.PsiSnippetDocTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JavaInjectedFileChangesHandlerProvider implements InjectedFileChangesHandlerProvider {

  @Override
  public InjectedFileChangesHandler createFileChangesHandler(List<? extends PsiLanguageInjectionHost.Shred> shreds,
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

  private static boolean hasSnippet(List<? extends PsiLanguageInjectionHost.Shred> shreds) {
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      if (shred.getHost() instanceof PsiSnippetDocTag) return true;
    }
    return false;
  }

  private static boolean hasBlockLiterals(List<? extends PsiLanguageInjectionHost.Shred> shreds) {
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      if (isTextBlock(shred.getHost())) return true;
    }
    return false;
  }

  static boolean isTextBlock(@Nullable PsiLanguageInjectionHost host) {
    if (host instanceof PsiLiteralExpression literal) return literal.isTextBlock();
    if (host instanceof PsiFragment fragment) return fragment.isTextBlock();
    return false;
  }
}

class OldJavaInjectedFileChangesHandler extends BaseInjectedFileChangesHandler {

  private final @NotNull RangeMarker myAltFullRange;
  /** {@code true} if every host is a text block, so a quote may be written into the host as is */
  private final boolean myTextBlockHosts;

  OldJavaInjectedFileChangesHandler(List<? extends PsiLanguageInjectionHost.Shred> shreds, Editor editor,
                                    Document newDocument,
                                    PsiFile injectedFile) {
    super(editor, newDocument, injectedFile);

    boolean textBlockHosts = true;
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      textBlockHosts &= JavaInjectedFileChangesHandlerProvider.isTextBlock(shred.getHost());
    }
    myTextBlockHosts = textBlockHosts;

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
    if (commitChangedRangeOnly(e)) return;

    final PsiFile origPsiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myHostDocument);
    String newText = myFragmentDocument.getText();
    // prepare guarded blocks
    Map<String, String> replacementMap = new LinkedHashMap<>();
    int count = 0;
    for (RangeMarker o : ContainerUtil.reverse(((DocumentEx)myFragmentDocument).getGuardedBlocks())) {
      String replacement = o.getUserData(QuickEditHandler.REPLACEMENT_KEY);
      String tempText = "REPLACE" + (count++) + Long.toHexString(StringHash.buz(replacement));
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
      if (origPsiFile != null && origPsiFile.isPhysical()) {
        CodeStyleManager.getInstance(myProject).reformatRange(
          origPsiFile, hostStartOffset, myAltFullRange.getEndOffset(), true);
      }
    }
    catch (IncorrectOperationException e1) {
      //LOG.error(e);
    }

    PsiElement newInjected = InjectedLanguageManager.getInstance(myProject).findInjectedElementAt(origPsiFile, hostStartOffset);
    DocumentWindow documentWindow = newInjected == null ? null : InjectedLanguageUtil.getDocumentWindow(newInjected);
    if (documentWindow != null) {
      moveCaretTo(documentWindow.injectedToHost(e.getOffset()));
    }
  }

  /**
   * Inserts {@code e.getNewFragment()} into the host as is, replacing only the host range the change maps to.
   * <p>
   * Rewriting the whole region loses text the host has but the fragment doesn't: a text block strips the trailing whitespace
   * of its lines (JLS 3.10.6), so lines nobody edited come back changed. A shred is a verbatim copy of a host range
   * (see {@code DocumentWindowImpl.calcText}), so a change inside one shred maps to the host by a constant shift.
   *
   * @return {@code true} if the change has been written back, {@code false} if the caller has to rewrite the whole region
   */
  private boolean commitChangedRangeOnly(@NotNull DocumentEvent e) {
    if (!((DocumentEx)myFragmentDocument).getGuardedBlocks().isEmpty()) return false;
    CharSequence newFragment = e.getNewFragment();
    if (!isWritableAsIs(newFragment)) return false;

    DocumentWindow window = findWindow();
    if (window == null) return false;

    // the fragment document already has the change applied, while the window still shows the untouched host
    CharSequence windowText = window.getImmutableCharSequence();
    int start = e.getOffset();
    int oldEnd = start + e.getOldLength();
    if (windowText.length() != myFragmentDocument.getTextLength() - e.getNewLength() + e.getOldLength()) return false;
    if (oldEnd > windowText.length() || !CharArrayUtil.regionMatches(windowText, start, e.getOldFragment())) return false;

    // a change crossing a shred border has no single constant shift, so it is left to the caller
    Segment shred = null;
    int shredStart = -1;
    for (Segment hostRange : window.getHostRanges()) {
      int candidateStart = window.hostToInjected(hostRange.getStartOffset());
      int candidateEnd = candidateStart + (hostRange.getEndOffset() - hostRange.getStartOffset());
      if (start < candidateStart || oldEnd > candidateEnd) continue;
      if (shred == null || start < candidateEnd) {
        shred = hostRange;
        shredStart = candidateStart;
        // an insertion right at a shred end fits the next shred as well, and only there it lands after the text
        // separating the shreds - inserting before the stripped text block indent would change the indent of the whole block
        if (start < candidateEnd) break;
      }
    }
    if (shred == null) return false;

    CharSequence hostChars = myHostDocument.getCharsSequence();
    int shredLength = shred.getEndOffset() - shred.getStartOffset();
    // the shift is only constant while the shred still matches the host text it was built from
    if (!CharArrayUtil.regionMatches(hostChars, shred.getStartOffset(),
                                     windowText.subSequence(shredStart, shredStart + shredLength))) {
      return false;
    }
    int shift = shred.getStartOffset() - shredStart;
    int hostStart = start + shift;
    int hostEnd = oldEnd + shift;
    if (createsTextBlockDelimiter(hostChars, hostStart, hostEnd, newFragment)) return false;

    myHostDocument.replaceString(hostStart, hostEnd, newFragment);
    PsiDocumentManager.getInstance(myProject).commitDocument(myHostDocument);
    moveCaretTo(hostStart + newFragment.length());
    return true;
  }

  /**
   * @return the window of the injection this handler serves, or {@code null} if there is none.
   * The injected file is re-read from the host, because a previous write-back may have replaced it.
   */
  private @Nullable DocumentWindow findWindow() {
    if (myInjectedFile != null && myInjectedFile.isValid()) {
      DocumentWindow window = InjectedLanguageUtil.getDocumentWindow(myInjectedFile);
      if (isUsable(window)) return window;
    }
    if (!myAltFullRange.isValid()) return null;
    PsiFile hostPsiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myHostDocument);
    if (hostPsiFile == null) return null;
    PsiElement injected = InjectedLanguageManager.getInstance(myProject)
      .findInjectedElementAt(hostPsiFile, myAltFullRange.getStartOffset());
    if (injected == null) return null;
    DocumentWindow window = InjectedLanguageUtil.getDocumentWindow(injected);
    if (!isUsable(window)) return null;
    myInjectedFile = injected.getContainingFile();
    return window;
  }

  private boolean isUsable(@Nullable DocumentWindow window) {
    return window != null && window.isValid() && window.getDelegate() == myHostDocument;
  }

  /** @return {@code true} if the text means the same inside the host literal, so it needs neither escaping nor re-indenting */
  private boolean isWritableAsIs(@NotNull CharSequence text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      // a backslash would start an escape sequence, a line break would need the text block indent added after it,
      // and a quote stands for itself only inside a text block
      if (c == '\\' || c == '\n' || c == '\r') return false;
      if ((c == '"' || c == '\'') && !myTextBlockHosts) return false;
    }
    return true;
  }

  private static boolean createsTextBlockDelimiter(@NotNull CharSequence hostChars, int hostStart, int hostEnd,
                                                  @NotNull CharSequence newFragment) {
    int from = Math.max(0, hostStart - 2);
    int to = Math.min(hostChars.length(), hostEnd + 2);
    String around = hostChars.subSequence(from, hostStart).toString() + newFragment + hostChars.subSequence(hostEnd, to);
    return around.contains("\"\"\"");
  }

  private void moveCaretTo(int hostOffset) {
    myHostEditor.getCaretModel().moveToOffset(hostOffset);
    myHostEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
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
