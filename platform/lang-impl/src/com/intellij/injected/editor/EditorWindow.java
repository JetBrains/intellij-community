/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.injected.editor;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.TextDrawingCallback;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.Iterator;

/**
 * @author Alexey
 */
public class EditorWindow extends UserDataHolderBase implements EditorEx {
  private final DocumentWindowImpl myDocumentWindow;
  private final EditorImpl myDelegate;
  private volatile PsiFile myInjectedFile;
  private final boolean myOneLine;
  private final CaretModelWindow myCaretModelDelegate;
  private final SelectionModelWindow mySelectionModelDelegate;
  private static final WeakList<EditorWindow> allEditors = new WeakList<EditorWindow>();
  private boolean myDisposed;
  private final MarkupModelWindow myMarkupModelDelegate;
  private final FoldingModelWindow myFoldingModelWindow;

  public static Editor create(@NotNull final DocumentWindowImpl documentRange, @NotNull final EditorImpl editor, @NotNull final PsiFile injectedFile) {
    assert documentRange.isValid();
    assert injectedFile.isValid();
    EditorWindow window;
    synchronized (allEditors) {
      for (EditorWindow editorWindow : allEditors) {
        if (editorWindow.getDocument() == documentRange && editorWindow.getDelegate() == editor) {
          editorWindow.myInjectedFile = injectedFile;
          if (editorWindow.isValid()) {
            return editorWindow;
          }
        }
        if (editorWindow.getDocument().areRangesEqual(documentRange)) {
          //int i = 0;
        }
      }
      window = new EditorWindow(documentRange, editor, injectedFile, documentRange.isOneLine());
      allEditors.add(window);
    }
    assert window.isValid();
    return window;
  }

  private EditorWindow(@NotNull DocumentWindowImpl documentWindow, @NotNull final EditorImpl delegate, @NotNull PsiFile injectedFile, boolean oneLine) {
    myDocumentWindow = documentWindow;
    myDelegate = delegate;
    myInjectedFile = injectedFile;
    myOneLine = oneLine;
    myCaretModelDelegate = new CaretModelWindow(myDelegate.getCaretModel(), this);
    mySelectionModelDelegate = new SelectionModelWindow(myDelegate, myDocumentWindow,this);
    myMarkupModelDelegate = new MarkupModelWindow((MarkupModelEx)myDelegate.getMarkupModel(), myDocumentWindow);
    myFoldingModelWindow = new FoldingModelWindow((FoldingModelEx)delegate.getFoldingModel(), documentWindow, this);
  }

  public static void disposeInvalidEditors() {
    Iterator<EditorWindow> iterator = allEditors.iterator();
    while (iterator.hasNext()) {
      EditorWindow editorWindow = iterator.next();
      if (!editorWindow.isValid()/* || myDocumentWindow.intersects(editorWindow.myDocumentWindow)*/) {
        editorWindow.dispose();

        InjectedLanguageUtil.clearCaches(editorWindow.getInjectedFile());
        iterator.remove();
      }
    }
  }

  private boolean isValid() {
    return !isDisposed() && myInjectedFile.isValid() && myDocumentWindow.isValid();
  }

  public PsiFile getInjectedFile() {
    return myInjectedFile;
  }
  public LogicalPosition hostToInjected(LogicalPosition pos) {
    assert isValid();
    int offsetInInjected = myDocumentWindow.hostToInjected(myDelegate.logicalPositionToOffset(pos));
    return offsetToLogicalPosition(offsetInInjected);
  }

  public LogicalPosition injectedToHost(LogicalPosition pos) {
    assert isValid();
    int offsetInHost = myDocumentWindow.injectedToHost(logicalPositionToOffset(pos));
    return myDelegate.offsetToLogicalPosition(offsetInHost);
  }

  private void dispose() {
    assert !myDisposed;
    myCaretModelDelegate.disposeModel();

    for (EditorMouseListener wrapper : myEditorMouseListeners.wrappers()) {
      myDelegate.removeEditorMouseListener(wrapper);
    }
    myEditorMouseListeners.clear();
    for (EditorMouseMotionListener wrapper : myEditorMouseMotionListeners.wrappers()) {
      myDelegate.removeEditorMouseMotionListener(wrapper);
    }
    myEditorMouseMotionListeners.clear();

    getDocument().dispose();
    myDisposed = true;
  }

  public boolean isViewer() {
    return myDelegate.isViewer();
  }

  public boolean isRendererMode() {
    return myDelegate.isRendererMode();
  }

  public void setRendererMode(final boolean isRendererMode) {
    myDelegate.setRendererMode(isRendererMode);
  }

  public void setFile(final VirtualFile vFile) {
    myDelegate.setFile(vFile);
  }

  public void setHeaderComponent(@Nullable JComponent header) {

  }

  public boolean hasHeaderComponent() {
    return false;
  }

  @Nullable
  public JComponent getHeaderComponent() {
    return null;
  }

  @Override
  public TextDrawingCallback getTextDrawingCallback() {
    return myDelegate.getTextDrawingCallback();
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return mySelectionModelDelegate;
  }

  @NotNull
  public MarkupModel getMarkupModel() {
    return myMarkupModelDelegate;
  }

  @NotNull
  public FoldingModelEx getFoldingModel() {
    return myFoldingModelWindow;
  }

  @NotNull
  public CaretModel getCaretModel() {
    return myCaretModelDelegate;
  }

  @NotNull
  public ScrollingModel getScrollingModel() {
    return myDelegate.getScrollingModel();
  }

  @NotNull
  public SoftWrapModel getSoftWrapModel() {
    return myDelegate.getSoftWrapModel();
  }

  @NotNull
  public EditorSettings getSettings() {
    return myDelegate.getSettings();
  }

  public void reinitSettings() {
    myDelegate.reinitSettings();
  }

  public void setFontSize(final int fontSize) {
    myDelegate.setFontSize(fontSize);
  }

  public void setHighlighter(final EditorHighlighter highlighter) {
    myDelegate.setHighlighter(highlighter);
  }

  public EditorHighlighter getHighlighter() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(myInjectedFile.getVirtualFile(), scheme, getProject());
    highlighter.setText(getDocument().getText());
    return highlighter;
  }

  @NotNull
  public JComponent getContentComponent() {
    return myDelegate.getContentComponent();
  }

  public EditorGutterComponentEx getGutterComponentEx() {
    return myDelegate.getGutterComponentEx();
  }

  public void addPropertyChangeListener(final PropertyChangeListener listener) {
    myDelegate.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(final PropertyChangeListener listener) {
    myDelegate.removePropertyChangeListener(listener);
  }

  public void setInsertMode(final boolean mode) {
    myDelegate.setInsertMode(mode);
  }

  public boolean isInsertMode() {
    return myDelegate.isInsertMode();
  }

  public void setColumnMode(final boolean mode) {
    myDelegate.setColumnMode(mode);
  }

  public boolean isColumnMode() {
    return myDelegate.isColumnMode();
  }

  @NotNull
  public VisualPosition xyToVisualPosition(@NotNull final Point p) {
    return logicalToVisualPosition(xyToLogicalPosition(p));
  }

  @NotNull
  public VisualPosition offsetToVisualPosition(final int offset) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset));
  }

  @NotNull
  public LogicalPosition offsetToLogicalPosition(final int offset) {
    return offsetToLogicalPosition(offset, true);
  }

  @NotNull
  public LogicalPosition offsetToLogicalPosition(final int offset, boolean softWrapAware) {
    assert isValid();
    int lineNumber = myDocumentWindow.getLineNumber(offset);
    int lineStartOffset = myDocumentWindow.getLineStartOffset(lineNumber);
    int column = calcLogicalColumnNumber(offset-lineStartOffset, lineNumber, lineStartOffset);
    return new LogicalPosition(lineNumber, column);
  }

  @Override
  public boolean isCaretActive() {
    return myDelegate.isCaretActive();
  }

  @Override
  public EditorColorsScheme createBoundColorSchemeDelegate(@Nullable EditorColorsScheme customGlobalScheme) {
    return myDelegate.createBoundColorSchemeDelegate(customGlobalScheme);
  }

  @NotNull
  public LogicalPosition xyToLogicalPosition(@NotNull final Point p) {
    assert isValid();
    LogicalPosition hostPos = myDelegate.xyToLogicalPosition(p);
    return hostToInjected(hostPos);
  }

  private LogicalPosition fitInsideEditor(LogicalPosition pos) {
    int lineCount = myDocumentWindow.getLineCount();
    if (pos.line >= lineCount) {
      pos = new LogicalPosition(lineCount-1, pos.column);
    }
    int lineLength = myDocumentWindow.getLineEndOffset(pos.line) - myDocumentWindow.getLineStartOffset(pos.line);
    if (pos.column >= lineLength) {
      pos = new LogicalPosition(pos.line, lineLength-1);
    }
    return pos;
  }

  @NotNull
  public Point logicalPositionToXY(@NotNull final LogicalPosition pos) {
    assert isValid();
    return myDelegate.logicalPositionToXY(injectedToHost(fitInsideEditor(pos)));
  }

  @NotNull
  public Point visualPositionToXY(@NotNull final VisualPosition pos) {
    assert isValid();
    return logicalPositionToXY(visualToLogicalPosition(pos));
  }

  public void repaint(final int startOffset, final int endOffset) {
    assert isValid();
    myDelegate.repaint(myDocumentWindow.injectedToHost(startOffset), myDocumentWindow.injectedToHost(endOffset));
  }

  @NotNull
  public DocumentWindowImpl getDocument() {
    return myDocumentWindow;
  }

  @NotNull
  public JComponent getComponent() {
    return myDelegate.getComponent();
  }

  private final ListenerWrapperMap<EditorMouseListener> myEditorMouseListeners = new ListenerWrapperMap<EditorMouseListener>();
  public void addEditorMouseListener(@NotNull final EditorMouseListener listener) {
    assert isValid();
    EditorMouseListener wrapper = new EditorMouseListener() {
      public void mousePressed(EditorMouseEvent e) {
        listener.mousePressed(new EditorMouseEvent(EditorWindow.this, e.getMouseEvent(), e.getArea()));
      }

      public void mouseClicked(EditorMouseEvent e) {
        listener.mouseClicked(new EditorMouseEvent(EditorWindow.this, e.getMouseEvent(), e.getArea()));
      }

      public void mouseReleased(EditorMouseEvent e) {
        listener.mouseReleased(new EditorMouseEvent(EditorWindow.this, e.getMouseEvent(), e.getArea()));
      }

      public void mouseEntered(EditorMouseEvent e) {
        listener.mouseEntered(new EditorMouseEvent(EditorWindow.this, e.getMouseEvent(), e.getArea()));
      }

      public void mouseExited(EditorMouseEvent e) {
        listener.mouseExited(new EditorMouseEvent(EditorWindow.this, e.getMouseEvent(), e.getArea()));
      }
    };
    myEditorMouseListeners.registerWrapper(listener, wrapper);

    myDelegate.addEditorMouseListener(wrapper);
  }

  public void removeEditorMouseListener(@NotNull final EditorMouseListener listener) {
    EditorMouseListener wrapper = myEditorMouseListeners.removeWrapper(listener);
    // HintManager might have an old editor instance
    if (wrapper != null) {
      myDelegate.removeEditorMouseListener(wrapper);
    }
  }

  private final ListenerWrapperMap<EditorMouseMotionListener> myEditorMouseMotionListeners = new ListenerWrapperMap<EditorMouseMotionListener>();
  public void addEditorMouseMotionListener(@NotNull final EditorMouseMotionListener listener) {
    assert isValid();
    EditorMouseMotionListener wrapper = new EditorMouseMotionListener() {
      public void mouseMoved(EditorMouseEvent e) {
        listener.mouseMoved(new EditorMouseEvent(EditorWindow.this, e.getMouseEvent(), e.getArea()));
      }

      public void mouseDragged(EditorMouseEvent e) {
        listener.mouseDragged(new EditorMouseEvent(EditorWindow.this, e.getMouseEvent(), e.getArea()));
      }
    };
    myEditorMouseMotionListeners.registerWrapper(listener, wrapper);
    myDelegate.addEditorMouseMotionListener(wrapper);
  }

  public void removeEditorMouseMotionListener(@NotNull final EditorMouseMotionListener listener) {
    EditorMouseMotionListener wrapper = myEditorMouseMotionListeners.removeWrapper(listener);
    if (wrapper != null) {
      myDelegate.removeEditorMouseMotionListener(wrapper);
    }
  }

  public boolean isDisposed() {
    return !myDisposed && myDelegate.isDisposed();
  }

  public void setBackgroundColor(final Color color) {
    myDelegate.setBackgroundColor(color);
  }

  public Color getBackgroundColor() {
    return myDelegate.getBackgroundColor();
  }

  public int getMaxWidthInRange(final int startOffset, final int endOffset) {
    return myDelegate.getMaxWidthInRange(startOffset, endOffset);
  }

  public int getLineHeight() {
    return myDelegate.getLineHeight();
  }

  public Dimension getContentSize() {
    return myDelegate.getContentSize();
  }

  public JScrollPane getScrollPane() {
    return myDelegate.getScrollPane();
  }

  @Override
  public void setBorder(Border border) {
    myDelegate.setBorder(border);
  }

  public int logicalPositionToOffset(@NotNull final LogicalPosition pos) {
    int lineStartOffset = myDocumentWindow.getLineStartOffset(pos.line);
    return calcOffset(pos.column, pos.line, lineStartOffset);
  }
  private int calcLogicalColumnNumber(int offsetInLine, int lineNumber, int lineStartOffset) {
    if (myDocumentWindow.getTextLength() == 0) return 0;

    if (offsetInLine==0) return 0;
    int end = myDocumentWindow.getLineEndOffset(lineNumber);
    if (offsetInLine > end- lineStartOffset) offsetInLine = end - lineStartOffset;

    CharSequence text = myDocumentWindow.getCharsSequence();
    return EditorUtil.calcColumnNumber(this, text, lineStartOffset, lineStartOffset +offsetInLine, EditorUtil.getTabSize(myDelegate));
  }
  private int calcOffset(int col, int lineNumber, int lineStartOffset) {
    if (myDocumentWindow.getTextLength() == 0) return 0;

    int end = myDocumentWindow.getLineEndOffset(lineNumber);

    CharSequence text = myDocumentWindow.getCharsSequence();
    return EditorUtil.calcOffset(this, text, lineStartOffset, end, col, EditorUtil.getTabSize(myDelegate));
  }

  public void setLastColumnNumber(final int val) {
    myDelegate.setLastColumnNumber(val);
  }

  public int getLastColumnNumber() {
    return myDelegate.getLastColumnNumber();
  }

  @NotNull
  @Override
  public VisualPosition logicalToVisualPosition(@NotNull LogicalPosition logicalPos, boolean softWrapAware) {
    assert isValid();
    return new VisualPosition(logicalPos.line, logicalPos.column);
  }

  // assuming there is no folding in injected documents
  @NotNull
  public VisualPosition logicalToVisualPosition(@NotNull final LogicalPosition pos) {
    return logicalToVisualPosition(pos, false);
  }

  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull final VisualPosition pos) {
    return visualToLogicalPosition(pos, true);
  }

  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull final VisualPosition pos, boolean softWrapAware) {
    assert isValid();
    return new LogicalPosition(pos.line, pos.column);
  }

  public DataContext getDataContext() {
    return myDelegate.getDataContext();
  }

  public EditorMouseEventArea getMouseEventArea(@NotNull final MouseEvent e) {
    return myDelegate.getMouseEventArea(e);
  }

  public boolean setCaretVisible(final boolean b) {
    return myDelegate.setCaretVisible(b);
  }

  public boolean setCaretEnabled(boolean enabled) {
    return myDelegate.setCaretEnabled(enabled);
  }

  public void addFocusListener(final FocusChangeListener listener) {
    myDelegate.addFocusListener(listener);
  }

  public Project getProject() {
    return myDelegate.getProject();
  }

  public boolean isOneLineMode() {
    return myOneLine;
  }

  public void setOneLineMode(final boolean isOneLineMode) {
    throw new UnsupportedOperationException();
  }

  public boolean isEmbeddedIntoDialogWrapper() {
    return myDelegate.isEmbeddedIntoDialogWrapper();
  }

  public void setEmbeddedIntoDialogWrapper(final boolean b) {
    myDelegate.setEmbeddedIntoDialogWrapper(b);
  }

  public VirtualFile getVirtualFile() {
    return myDelegate.getVirtualFile();
  }

  public void stopOptimizedScrolling() {
    myDelegate.stopOptimizedScrolling();
  }

  public CopyProvider getCopyProvider() {
    return myDelegate.getCopyProvider();
  }

  public CutProvider getCutProvider() {
    return myDelegate.getCutProvider();
  }

  public PasteProvider getPasteProvider() {
    return myDelegate.getPasteProvider();
  }

  public DeleteProvider getDeleteProvider() {
    return myDelegate.getDeleteProvider();
  }

  public void setColorsScheme(@NotNull final EditorColorsScheme scheme) {
    myDelegate.setColorsScheme(scheme);
  }

  @NotNull
  public EditorColorsScheme getColorsScheme() {
    return myDelegate.getColorsScheme();
  }

  public void setVerticalScrollbarOrientation(final int type) {
    myDelegate.setVerticalScrollbarOrientation(type);
  }

  public void setVerticalScrollbarVisible(final boolean b) {
    myDelegate.setVerticalScrollbarVisible(b);
  }

  public void setHorizontalScrollbarVisible(final boolean b) {
    myDelegate.setHorizontalScrollbarVisible(b);
  }

  public boolean processKeyTyped(final KeyEvent e) {
    return myDelegate.processKeyTyped(e);
  }

  @NotNull
  public EditorGutter getGutter() {
    return myDelegate.getGutter();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final EditorWindow that = (EditorWindow)o;

    DocumentWindow thatWindow = that.getDocument();
    return myDelegate.equals(that.myDelegate) && myDocumentWindow.equals(thatWindow);
  }

  public int hashCode() {
    return myDocumentWindow.hashCode();
  }

  public Editor getDelegate() {
    return myDelegate;
  }

  public int calcColumnNumber(final CharSequence text, final int start, final int offset, final int tabSize) {
    int hostStart = myDocumentWindow.injectedToHost(start);
    int hostOffset = myDocumentWindow.injectedToHost(offset);
    return myDelegate.calcColumnNumber(myDelegate.getDocument().getText(), hostStart, hostOffset, tabSize);
  }

  public int calcColumnNumber(int offset, int lineIndex) {
    return myDelegate.calcColumnNumber(myDocumentWindow.injectedToHost(offset), myDocumentWindow.injectedToHostLine(lineIndex));
  }

  public IndentsModel getIndentsModel() {
    return myDelegate.getIndentsModel();
  }
}
