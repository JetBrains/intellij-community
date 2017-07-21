/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.diagnostic.LogEventException;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.util.PsiUtilCore;

import java.util.List;

/**
 * @author peter
 */
class CompletionAssertions {

  static void assertCommitSuccessful(Editor editor, PsiFile psiFile) {
    Document document = editor.getDocument();
    int docLength = document.getTextLength();
    int psiLength = psiFile.getTextLength();
    PsiDocumentManager manager = PsiDocumentManager.getInstance(psiFile.getProject());
    boolean committed = !manager.isUncommited(document);
    if (docLength == psiLength && committed) {
      return;
    }

    FileViewProvider viewProvider = psiFile.getViewProvider();

    String message = "unsuccessful commit:";
    message += "\nmatching=" + (psiFile == manager.getPsiFile(document));
    message += "\ninjectedEditor=" + (editor instanceof EditorWindow);
    message += "\ninjectedFile=" + InjectedLanguageManager.getInstance(psiFile.getProject()).isInjectedFragment(psiFile);
    message += "\ncommitted=" + committed;
    message += "\nfile=" + psiFile.getName();
    message += "\nfile class=" + psiFile.getClass();
    message += "\nfile.valid=" + psiFile.isValid();
    message += "\nfile.physical=" + psiFile.isPhysical();
    message += "\nfile.eventSystemEnabled=" + viewProvider.isEventSystemEnabled();
    message += "\nlanguage=" + psiFile.getLanguage();
    message += "\ndoc.length=" + docLength;
    message += "\npsiFile.length=" + psiLength;
    String fileText = psiFile.getText();
    if (fileText != null) {
      message += "\npsiFile.text.length=" + fileText.length();
    }
    FileASTNode node = psiFile.getNode();
    if (node != null) {
      message += "\nnode.length=" + node.getTextLength();
      String nodeText = node.getText();
      message += "\nnode.text.length=" + nodeText.length();
    }
    VirtualFile virtualFile = viewProvider.getVirtualFile();
    message += "\nvirtualFile=" + virtualFile;
    message += "\nvirtualFile.class=" + virtualFile.getClass();
    message += "\n" + DebugUtil.currentStackTrace();

    throw new LogEventException("Commit unsuccessful", message,
                                new Attachment(virtualFile.getPath() + "_file.txt", StringUtil.notNullize(fileText)),
                                createAstAttachment(psiFile, psiFile),
                                new Attachment("docText.txt", document.getText()));
  }

  static void checkEditorValid(Editor editor) {
    if (!isEditorValid(editor)) {
      throw new AssertionError();
    }
  }

  static boolean isEditorValid(Editor editor) {
    return !(editor instanceof EditorWindow) || ((EditorWindow)editor).isValid();
  }

  private static Attachment createAstAttachment(PsiFile fileCopy, final PsiFile originalFile) {
    return new Attachment(originalFile.getViewProvider().getVirtualFile().getPath() + " syntactic tree.txt", DebugUtil.psiToString(fileCopy, false, true));
  }

  private static Attachment createFileTextAttachment(PsiFile fileCopy, final PsiFile originalFile) {
    return new Attachment(originalFile.getViewProvider().getVirtualFile().getPath(), fileCopy.getText());
  }

  static void assertFinalOffsets(PsiFile originalFile, CompletionContext context, PsiFile injected) {
    if (context.getStartOffset() >= context.file.getTextLength()) {
      String msg = "start outside the file; file=" + context.file + " " + context.file.getTextLength();
      msg += "; injected=" + (injected != null);
      msg += "; original " + originalFile + " " + originalFile.getTextLength();
      throw new AssertionError(msg);
    }
    assert context.getStartOffset() >= 0 : "start < 0";
  }

  static void assertInjectedOffsets(int hostStartOffset,
                                    InjectedLanguageManager injectedLanguageManager,
                                    PsiFile injected,
                                    DocumentWindow documentWindow) {
    assert documentWindow != null : "no DocumentWindow for an injected fragment";

    TextRange host = injectedLanguageManager.injectedToHost(injected, injected.getTextRange());
    assert hostStartOffset >= host.getStartOffset() : "startOffset before injected";
    assert hostStartOffset <= host.getEndOffset() : "startOffset after injected";
  }

  static void assertHostInfo(PsiFile hostCopy, OffsetMap hostMap) {
    PsiUtilCore.ensureValid(hostCopy);
    if (hostMap.getOffset(CompletionInitializationContext.START_OFFSET) >= hostCopy.getTextLength()) {
      throw new AssertionError("startOffset outside the host file: " + hostMap.getOffset(CompletionInitializationContext.START_OFFSET) + "; " + hostCopy);
    }
  }

  static void assertCompletionPositionPsiConsistent(CompletionContext newContext,
                                                    int offset,
                                                    PsiFile fileCopy,
                                                    PsiFile originalFile, PsiElement insertedElement) {
    if (insertedElement == null) {
      throw new LogEventException("No element at insertion offset",
                                                                   "offset=" +
                                                                   newContext.getStartOffset() +
                                                                   "\n" +
                                                                   DebugUtil.currentStackTrace(),
                                                                   createFileTextAttachment(fileCopy, originalFile),
                                                                   createAstAttachment(fileCopy, originalFile));
    }

    if (fileCopy.findElementAt(offset) != insertedElement) {
      throw new AssertionError("wrong offset");
    }

    final TextRange range = insertedElement.getTextRange();
    CharSequence fileCopyText = fileCopy.getViewProvider().getContents();
    if ((range.getEndOffset() > fileCopyText.length()) ||
        !fileCopyText.subSequence(range.getStartOffset(), range.getEndOffset()).toString().equals(insertedElement.getText())) {
      throw new LogEventException("Inconsistent completion tree", "range=" + range + "\n" + DebugUtil.currentStackTrace(),
                                         createFileTextAttachment(fileCopy, originalFile), createAstAttachment(fileCopy, originalFile),
                                         new Attachment("Element at caret.txt", insertedElement.getText()));
    }
  }

  static void assertCorrectOriginalFile(String prefix, PsiFile file, PsiFile copy) {
    if (copy.getOriginalFile() != file) {
      throw new AssertionError(prefix + " copied file doesn't have correct original: noOriginal=" + (copy.getOriginalFile() == copy) +
                               "\n file " + fileInfo(file) +
                               "\n copy " + fileInfo(copy));
    }
  }

  private static String fileInfo(PsiFile file) {
    return file + " of " + file.getClass() +
           " in " + file.getViewProvider() + ", languages=" + file.getViewProvider().getLanguages() +
           ", physical=" + file.isPhysical();
  }

  static class WatchingInsertionContext extends InsertionContext {
    private RangeMarkerEx tailWatcher;
    Throwable invalidateTrace;
    DocumentEvent killer;
    private RangeMarkerSpy spy;

    public WatchingInsertionContext(OffsetMap offsetMap, PsiFile file, char completionChar, List<LookupElement> items, Editor editor) {
      super(offsetMap, completionChar, items.toArray(new LookupElement[items.size()]),
            file, editor,
            completionChar != Lookup.AUTO_INSERT_SELECT_CHAR && completionChar != Lookup.REPLACE_SELECT_CHAR &&
            completionChar != Lookup.NORMAL_SELECT_CHAR);
    }

    @Override
    public void setTailOffset(int offset) {
      super.setTailOffset(offset);
      watchTail(offset);
    }

    private void watchTail(int offset) {
      stopWatching();
      tailWatcher = (RangeMarkerEx)getDocument().createRangeMarker(offset, offset);
      if (!tailWatcher.isValid()) {
        throw new AssertionError(getDocument() + "; offset=" + offset);
      }
      tailWatcher.setGreedyToRight(true);
      spy = new RangeMarkerSpy(tailWatcher) {
        @Override
        protected void invalidated(DocumentEvent e) {
          if (invalidateTrace == null) {
            invalidateTrace = new Throwable();
            killer = e;
          }
        }
      };
      getDocument().addDocumentListener(spy);
    }

    void stopWatching() {
      if (tailWatcher != null) {
        getDocument().removeDocumentListener(spy);
        tailWatcher.dispose();
      }
    }

    @Override
    public int getTailOffset() {
      if (!getOffsetMap().containsOffset(TAIL_OFFSET) && invalidateTrace != null) {
        throw new RuntimeExceptionWithAttachments("Tail offset invalid", new Attachment("invalidated", invalidateTrace));
      }

      int offset = super.getTailOffset();
      if (tailWatcher.getStartOffset() != tailWatcher.getEndOffset() && offset > 0) {
        watchTail(offset);
      }

      return offset;
    }
  }

}
