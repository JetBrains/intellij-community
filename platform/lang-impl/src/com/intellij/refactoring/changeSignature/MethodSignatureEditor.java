/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.CaretModelImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class MethodSignatureEditor<M extends PsiElement> extends EditorTextField {
  private static final String MARKER_START = "\n\n\n\n\n\n\n\n/*marker_start*/\n\n\n\n\n\n\n";
  private static final String MARKER_END = "\n\n\n\n\n\n\n\n/*marker_end*/\n\n\n\n\n\n\n";
  private final M myMethod;

  private final Class<? extends M> myClass;
  private final PsiFile myFile;
  private final String myStartMarker;
  private String myEndMarker;
  public static final Key<Integer> OLD_PARAMETER_INDEX = Key.create("change.signature.parameter.index");
  public static final ParameterIndexer INDEXER = new ParameterIndexer() {
    @Override
    public void setIndex(@NotNull PsiElement element, int index) {
      element.putCopyableUserData(OLD_PARAMETER_INDEX, index);
    }

    @Override
    public int getIndex(@NotNull PsiElement element) {
      final Integer index = element.getCopyableUserData(OLD_PARAMETER_INDEX);
      return index == null ? -1 : index.intValue();
    }
  };


  public MethodSignatureEditor(@NotNull M method, Class<? extends M> genericInterface) {
    super(EditorFactory.getInstance().createDocument(""), method.getProject(), method.getContainingFile().getFileType(), false, false);
    myStartMarker = getStartMarker();
    myEndMarker = getEndMarker();
    myMethod = method;
    myFile = method.getContainingFile();
    myClass = genericInterface;
    final Document document = createDocument();
    assert document != null : "Can't create document";
    setDocument(document);
  }

  protected final TextRange getCurrentSignatureTextRange() {
    final String text = getDocument().getText();
    return TextRange.create(text.indexOf(myStartMarker) + myStartMarker.length(), text.indexOf(myEndMarker));
  }

  public abstract TextRange getSignatureTextRange();

  protected abstract String formatMethod();

  protected abstract void indexParameters(M method, @NotNull ParameterIndexer indexer);

  protected String getStartMarker() {
    return MARKER_START;
  }

  protected String getEndMarker() {
    return MARKER_END;
  }

  @Nullable
  protected M createFromString() {
    final PsiFile file = PsiDocumentManager.getInstance(getProject()).getPsiFile(getDocument());
    if (file == null) return null;

    final int startOffset = getCurrentSignatureTextRange().getStartOffset();
    final int endOffset = file.getTextLength();
    PsiTreeUtil.findElementOfClassAtRange(file, startOffset , endOffset, myClass);

    final FileViewProvider viewProvider = file.getViewProvider();
    M result = null;
    for (Language lang : viewProvider.getLanguages()) {
      PsiElement elementAt = viewProvider.findElementAt(startOffset, lang);
      M run = PsiTreeUtil.getParentOfType(elementAt, myClass, false);
      M prev = run;
      while (run != null && run.getTextRange().getStartOffset() == startOffset &&
             run.getTextRange().getEndOffset() <= endOffset) {
        prev = run;
        run = PsiTreeUtil.getParentOfType(run, myClass);
      }

      if (prev == null) continue;
      final int elementStartOffset = prev.getTextRange().getStartOffset();
      final int elementEndOffset = prev.getTextRange().getEndOffset();
      return startOffset >= elementStartOffset && elementEndOffset <= endOffset ? prev : null;
    }

    return result;

  }


  public M getMethod() {
    return myMethod;
  }

  @Nullable
  private Document createDocument() {
    final StringBuilder text = new StringBuilder(myFile.getText());
    final TextRange range = getSignatureTextRange();
    text.insert(range.getEndOffset(), myEndMarker);
    text.insert(range.getStartOffset(), myStartMarker);
    text.replace(range.getStartOffset() + myStartMarker.length(), range.getEndOffset() + myStartMarker.length(), formatMethod());
    final PsiFile newFile = PsiFileFactory.getInstance(myMethod.getProject())
      .createFileFromText(myFile.getName(), myFile.getFileType(), text, System.currentTimeMillis(), true);
    return PsiDocumentManager.getInstance(myMethod.getProject()).getDocument(newFile);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    final Editor editor = getEditor();
    if (editor instanceof EditorImpl) {
      editor.putUserData(EditorImpl.EDITABLE_AREA_MARKER, Pair.create(myStartMarker, myEndMarker));

      indexParameters(createFromString(), INDEXER);

      ((EditorImpl)editor).setScrollToCaret(false);
      ((CaretModelImpl)editor.getCaretModel()).setIgnoreWrongMoves(true);
      final TextRange range = getCurrentSignatureTextRange();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          CodeStyleManager.getInstance(getProject()).reformatText(myFile, range.getStartOffset(), range.getEndOffset());
        }
      });
      editor.getCaretModel().addCaretListener(new CaretListener() {
        @Override
        public void caretPositionChanged(CaretEvent e) {
          createFromString();
          final LogicalPosition newPosition = e.getNewPosition();
          final Editor ed = e.getEditor();
          final int pos = ed.logicalPositionToOffset(newPosition);
          final TextRange range = getCurrentSignatureTextRange();
          final int start = range.getStartOffset();
          final int end = range.getEndOffset();
          if (pos < start) {
            e.getEditor().getCaretModel().moveToOffset(start);
            updateUI(ed, false);
          }
          else if (end < pos) {
            e.getEditor().getCaretModel().moveToOffset(end);
            updateUI(ed, false);
          }
        }
      });
      editor.getSettings().setUseSoftWraps(false);
      updateUI(editor, true);
      editor.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        public void documentChanged(DocumentEvent e) {
          updateUI(getEditor(), false);
        }
      });
    }
  }

  private void updateUI(final Editor editor, boolean moveCaretToStart) {
    final TextRange range = getCurrentSignatureTextRange();
    final int start = range.getStartOffset();
    final int end = range.getEndOffset();
    final int startLine = ((EditorImpl)editor).offsetToLogicalPosition(start, false).line;
    final int endLine = ((EditorImpl)editor).offsetToLogicalPosition(end, false).line;
    final int lineCount = Math.max(1, endLine - startLine);
    final Dimension old = getSize();
    final Dimension size = new Dimension(getWidth(), editor.getLineHeight() * (lineCount + 2) + 4);
    if (!old.equals(size)) {
      setSize(size);
      setPreferredSize(size);
      revalidate();
      repaint();
    }
    if (moveCaretToStart) {
      editor.getCaretModel().moveToOffset(start);
    }

    editor.getScrollingModel().scrollVertically((startLine - 1)* editor.getLineHeight() + 2);
  }
}
