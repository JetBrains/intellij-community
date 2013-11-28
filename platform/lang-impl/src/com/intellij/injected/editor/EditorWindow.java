/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.editor.highlighter.LightHighlighterClient;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.impl.TextDrawingCallback;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
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
import java.util.*;
import java.util.List;

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
  private static final List<EditorWindow> allEditors = new WeakList<EditorWindow>();
  private boolean myDisposed;
  private final MarkupModelWindow myMarkupModelDelegate;
  private final FoldingModelWindow myFoldingModelWindow;
  private final SoftWrapModelImpl mySoftWrapModel;

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
    myFoldingModelWindow = new FoldingModelWindow(delegate.getFoldingModel(), documentWindow, this);
    mySoftWrapModel = new SoftWrapModelImpl(this);
    Disposer.register(myDocumentWindow, mySoftWrapModel);
  }

  public static void disposeInvalidEditors() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    Iterator<EditorWindow> iterator = allEditors.iterator();
    while (iterator.hasNext()) {
      EditorWindow editorWindow = iterator.next();
      if (!editorWindow.isValid()) {
        editorWindow.dispose();

        InjectedLanguageUtil.clearCaches(editorWindow.myInjectedFile, editorWindow.getDocument());
        iterator.remove();
      }
    }
  }

  public boolean isValid() {
    return !isDisposed() && !myInjectedFile.getProject().isDisposed() && myInjectedFile.isValid() && myDocumentWindow.isValid();
  }

  public PsiFile getInjectedFile() {
    return myInjectedFile;
  }

  @NotNull
  public LogicalPosition hostToInjected(@NotNull LogicalPosition hPos) {
    assert isValid();
    DocumentEx hostDocument = myDelegate.getDocument();
    int hLineEndOffset = hPos.line >= hostDocument.getLineCount() ? hostDocument.getTextLength() : hostDocument.getLineEndOffset(hPos.line);
    LogicalPosition hLineEndPos = myDelegate.offsetToLogicalPosition(hLineEndOffset);
    if (hLineEndPos.column < hPos.column) {
      // in virtual space
      LogicalPosition iPos = myDocumentWindow.hostToInjectedInVirtualSpace(hPos);
      if (iPos != null) {
        return iPos;
      }
    }

    int hOffset = myDelegate.logicalPositionToOffset(hPos);
    int iOffset = myDocumentWindow.hostToInjected(hOffset);
    return offsetToLogicalPosition(iOffset);
  }

  @NotNull
  public LogicalPosition injectedToHost(@NotNull LogicalPosition pos) {
    assert isValid();
    // beware the virtual space
    int column = pos.column;
    int lineStartOffset = myDocumentWindow.getLineStartOffset(pos.line);
    int lineEndOffset = myDocumentWindow.getLineEndOffset(pos.line);
    if (column > lineEndOffset - lineStartOffset) {
      // in virtual space, calculate the host pos as an offset from the line end
      int delta = column - (lineEndOffset - lineStartOffset);

      int baseOffsetInHost = myDocumentWindow.injectedToHost(lineEndOffset);
      LogicalPosition lineStartPosInHost = myDelegate.offsetToLogicalPosition(baseOffsetInHost);
      return new LogicalPosition(lineStartPosInHost.line, lineStartPosInHost.column + delta);
    }
    else {
      int offset = lineStartOffset + column;
      int hostOffset = getDocument().injectedToHost(offset);
      return myDelegate.offsetToLogicalPosition(hostOffset);
    }
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

    myDisposed = true;
    Disposer.dispose(myDocumentWindow);
  }

  @Override
  public boolean isViewer() {
    return myDelegate.isViewer();
  }

  @Override
  public boolean isRendererMode() {
    return myDelegate.isRendererMode();
  }

  @Override
  public void setRendererMode(final boolean isRendererMode) {
    myDelegate.setRendererMode(isRendererMode);
  }

  @Override
  public void setFile(final VirtualFile vFile) {
    myDelegate.setFile(vFile);
  }

  @Override
  public void setHeaderComponent(@Nullable JComponent header) {

  }

  @Override
  public boolean hasHeaderComponent() {
    return false;
  }

  @Override
  @Nullable
  public JComponent getHeaderComponent() {
    return null;
  }

  @Override
  public TextDrawingCallback getTextDrawingCallback() {
    return myDelegate.getTextDrawingCallback();
  }

  @Override
  @NotNull
  public SelectionModel getSelectionModel() {
    return mySelectionModelDelegate;
  }

  @Override
  @NotNull
  public MarkupModel getMarkupModel() {
    return myMarkupModelDelegate;
  }

  @Override
  @NotNull
  public FoldingModelEx getFoldingModel() {
    return myFoldingModelWindow;
  }

  @Override
  @NotNull
  public CaretModel getCaretModel() {
    return myCaretModelDelegate;
  }

  @Override
  @NotNull
  public ScrollingModelEx getScrollingModel() {
    return myDelegate.getScrollingModel();
  }

  @Override
  @NotNull
  public SoftWrapModelEx getSoftWrapModel() {
    return mySoftWrapModel;
  }

  @Override
  @NotNull
  public EditorSettings getSettings() {
    return myDelegate.getSettings();
  }

  @Override
  public void reinitSettings() {
    myDelegate.reinitSettings();
  }

  @Override
  public void setFontSize(final int fontSize) {
    myDelegate.setFontSize(fontSize);
  }

  @Override
  public void setHighlighter(@NotNull final EditorHighlighter highlighter) {
    myDelegate.setHighlighter(highlighter);
  }

  @NotNull
  @Override
  public EditorHighlighter getHighlighter() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(myInjectedFile.getVirtualFile(), scheme, getProject());
    highlighter.setText(getDocument().getText());
    highlighter.setEditor(new LightHighlighterClient(getDocument(), getProject()));
    return highlighter;
  }

  @Override
  public JComponent getPermanentHeaderComponent() {
    return myDelegate.getPermanentHeaderComponent();
  }

  @Override
  public void setPermanentHeaderComponent(JComponent component) {
    myDelegate.setPermanentHeaderComponent(component);
  }

  @Override
  @NotNull
  public JComponent getContentComponent() {
    return myDelegate.getContentComponent();
  }

  @NotNull
  @Override
  public EditorGutterComponentEx getGutterComponentEx() {
    return myDelegate.getGutterComponentEx();
  }

  @Override
  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myDelegate.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myDelegate.removePropertyChangeListener(listener);
  }

  @Override
  public void setInsertMode(final boolean mode) {
    myDelegate.setInsertMode(mode);
  }

  @Override
  public boolean isInsertMode() {
    return myDelegate.isInsertMode();
  }

  @Override
  public void setColumnMode(final boolean mode) {
    myDelegate.setColumnMode(mode);
  }

  @Override
  public boolean isColumnMode() {
    return myDelegate.isColumnMode();
  }

  @Override
  @NotNull
  public VisualPosition xyToVisualPosition(@NotNull final Point p) {
    return logicalToVisualPosition(xyToLogicalPosition(p));
  }

  @Override
  @NotNull
  public VisualPosition offsetToVisualPosition(final int offset) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset));
  }

  @Override
  @NotNull
  public LogicalPosition offsetToLogicalPosition(final int offset) {
    return offsetToLogicalPosition(offset, true);
  }

  @Override
  @NotNull
  public LogicalPosition offsetToLogicalPosition(final int offset, boolean softWrapAware) {
    assert isValid();
    int lineNumber = myDocumentWindow.getLineNumber(offset);
    int lineStartOffset = myDocumentWindow.getLineStartOffset(lineNumber);
    int column = calcLogicalColumnNumber(offset-lineStartOffset, lineNumber, lineStartOffset);
    return new LogicalPosition(lineNumber, column);
  }

  @NotNull
  @Override
  public EditorColorsScheme createBoundColorSchemeDelegate(@Nullable EditorColorsScheme customGlobalScheme) {
    return myDelegate.createBoundColorSchemeDelegate(customGlobalScheme);
  }

  @Override
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
      pos = new LogicalPosition(pos.line, Math.max(0, lineLength-1));
    }
    return pos;
  }

  @Override
  @NotNull
  public Point logicalPositionToXY(@NotNull final LogicalPosition pos) {
    assert isValid();
    return myDelegate.logicalPositionToXY(injectedToHost(fitInsideEditor(pos)));
  }

  @Override
  @NotNull
  public Point visualPositionToXY(@NotNull final VisualPosition pos) {
    assert isValid();
    return logicalPositionToXY(visualToLogicalPosition(pos));
  }

  @Override
  public void repaint(final int startOffset, final int endOffset) {
    assert isValid();
    myDelegate.repaint(myDocumentWindow.injectedToHost(startOffset), myDocumentWindow.injectedToHost(endOffset));
  }

  @Override
  @NotNull
  public DocumentWindowImpl getDocument() {
    return myDocumentWindow;
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myDelegate.getComponent();
  }

  private final ListenerWrapperMap<EditorMouseListener> myEditorMouseListeners = new ListenerWrapperMap<EditorMouseListener>();
  @Override
  public void addEditorMouseListener(@NotNull final EditorMouseListener listener) {
    assert isValid();
    EditorMouseListener wrapper = new EditorMouseListener() {
      @Override
      public void mousePressed(EditorMouseEvent e) {
        listener.mousePressed(new EditorMouseEvent(EditorWindow.this, e.getMouseEvent(), e.getArea()));
      }

      @Override
      public void mouseClicked(EditorMouseEvent e) {
        listener.mouseClicked(new EditorMouseEvent(EditorWindow.this, e.getMouseEvent(), e.getArea()));
      }

      @Override
      public void mouseReleased(EditorMouseEvent e) {
        listener.mouseReleased(new EditorMouseEvent(EditorWindow.this, e.getMouseEvent(), e.getArea()));
      }

      @Override
      public void mouseEntered(EditorMouseEvent e) {
        listener.mouseEntered(new EditorMouseEvent(EditorWindow.this, e.getMouseEvent(), e.getArea()));
      }

      @Override
      public void mouseExited(EditorMouseEvent e) {
        listener.mouseExited(new EditorMouseEvent(EditorWindow.this, e.getMouseEvent(), e.getArea()));
      }
    };
    myEditorMouseListeners.registerWrapper(listener, wrapper);

    myDelegate.addEditorMouseListener(wrapper);
  }

  @Override
  public void removeEditorMouseListener(@NotNull final EditorMouseListener listener) {
    EditorMouseListener wrapper = myEditorMouseListeners.removeWrapper(listener);
    // HintManager might have an old editor instance
    if (wrapper != null) {
      myDelegate.removeEditorMouseListener(wrapper);
    }
  }

  private final ListenerWrapperMap<EditorMouseMotionListener> myEditorMouseMotionListeners = new ListenerWrapperMap<EditorMouseMotionListener>();
  @Override
  public void addEditorMouseMotionListener(@NotNull final EditorMouseMotionListener listener) {
    assert isValid();
    EditorMouseMotionListener wrapper = new EditorMouseMotionListener() {
      @Override
      public void mouseMoved(EditorMouseEvent e) {
        listener.mouseMoved(new EditorMouseEvent(EditorWindow.this, e.getMouseEvent(), e.getArea()));
      }

      @Override
      public void mouseDragged(EditorMouseEvent e) {
        listener.mouseDragged(new EditorMouseEvent(EditorWindow.this, e.getMouseEvent(), e.getArea()));
      }
    };
    myEditorMouseMotionListeners.registerWrapper(listener, wrapper);
    myDelegate.addEditorMouseMotionListener(wrapper);
  }

  @Override
  public void removeEditorMouseMotionListener(@NotNull final EditorMouseMotionListener listener) {
    EditorMouseMotionListener wrapper = myEditorMouseMotionListeners.removeWrapper(listener);
    if (wrapper != null) {
      myDelegate.removeEditorMouseMotionListener(wrapper);
    }
  }

  @Override
  public boolean isDisposed() {
    return !myDisposed && myDelegate.isDisposed();
  }

  @Override
  public void setBackgroundColor(final Color color) {
    myDelegate.setBackgroundColor(color);
  }

  @Override
  public Color getBackgroundColor() {
    return myDelegate.getBackgroundColor();
  }

  @Override
  public int getMaxWidthInRange(final int startOffset, final int endOffset) {
    return myDelegate.getMaxWidthInRange(startOffset, endOffset);
  }

  @Override
  public int getLineHeight() {
    return myDelegate.getLineHeight();
  }

  @Override
  public Dimension getContentSize() {
    return myDelegate.getContentSize();
  }

  @NotNull
  @Override
  public JScrollPane getScrollPane() {
    return myDelegate.getScrollPane();
  }

  @Override
  public void setBorder(Border border) {
    myDelegate.setBorder(border);
  }

  @Override
  public Insets getInsets() {
    return myDelegate.getInsets();
  }

  @Override
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
    return EditorUtil.calcOffset(this, text, lineStartOffset, end, col, EditorUtil.getTabSize(myDelegate), null);
  }

  @Override
  public void setLastColumnNumber(final int val) {
    myDelegate.setLastColumnNumber(val);
  }

  @Override
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
  @Override
  @NotNull
  public VisualPosition logicalToVisualPosition(@NotNull final LogicalPosition pos) {
    return logicalToVisualPosition(pos, false);
  }

  @Override
  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull final VisualPosition pos) {
    return visualToLogicalPosition(pos, true);
  }

  @Override
  @NotNull
  public LogicalPosition visualToLogicalPosition(@NotNull final VisualPosition pos, boolean softWrapAware) {
    assert isValid();
    return new LogicalPosition(pos.line, pos.column);
  }

  @NotNull
  @Override
  public DataContext getDataContext() {
    return myDelegate.getDataContext();
  }

  @Override
  public EditorMouseEventArea getMouseEventArea(@NotNull final MouseEvent e) {
    return myDelegate.getMouseEventArea(e);
  }

  @Override
  public boolean setCaretVisible(final boolean b) {
    return myDelegate.setCaretVisible(b);
  }

  @Override
  public boolean setCaretEnabled(boolean enabled) {
    return myDelegate.setCaretEnabled(enabled);
  }

  @Override
  public void addFocusListener(@NotNull final FocusChangeListener listener) {
    myDelegate.addFocusListener(listener);
  }

  @Override
  public void addFocusListener(@NotNull FocusChangeListener listener, @NotNull Disposable parentDisposable) {
    myDelegate.addFocusListener(listener, parentDisposable);
  }

  @Override
  public Project getProject() {
    return myDelegate.getProject();
  }

  @Override
  public boolean isOneLineMode() {
    return myOneLine;
  }

  @Override
  public void setOneLineMode(final boolean isOneLineMode) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEmbeddedIntoDialogWrapper() {
    return myDelegate.isEmbeddedIntoDialogWrapper();
  }

  @Override
  public void setEmbeddedIntoDialogWrapper(final boolean b) {
    myDelegate.setEmbeddedIntoDialogWrapper(b);
  }

  @Override
  public VirtualFile getVirtualFile() {
    return myDelegate.getVirtualFile();
  }

  @Override
  public void stopOptimizedScrolling() {
    myDelegate.stopOptimizedScrolling();
  }

  @Override
  public CopyProvider getCopyProvider() {
    return myDelegate.getCopyProvider();
  }

  @Override
  public CutProvider getCutProvider() {
    return myDelegate.getCutProvider();
  }

  @Override
  public PasteProvider getPasteProvider() {
    return myDelegate.getPasteProvider();
  }

  @Override
  public DeleteProvider getDeleteProvider() {
    return myDelegate.getDeleteProvider();
  }

  @Override
  public void setColorsScheme(@NotNull final EditorColorsScheme scheme) {
    myDelegate.setColorsScheme(scheme);
  }

  @Override
  @NotNull
  public EditorColorsScheme getColorsScheme() {
    return myDelegate.getColorsScheme();
  }

  @Override
  public void setVerticalScrollbarOrientation(final int type) {
    myDelegate.setVerticalScrollbarOrientation(type);
  }

  @Override
  public int getVerticalScrollbarOrientation() {
    return myDelegate.getVerticalScrollbarOrientation();
  }

  @Override
  public void setVerticalScrollbarVisible(final boolean b) {
    myDelegate.setVerticalScrollbarVisible(b);
  }

  @Override
  public void setHorizontalScrollbarVisible(final boolean b) {
    myDelegate.setHorizontalScrollbarVisible(b);
  }

  @Override
  public boolean processKeyTyped(@NotNull final KeyEvent e) {
    return myDelegate.processKeyTyped(e);
  }

  @Override
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

  @Override
  public int calcColumnNumber(@NotNull final CharSequence text, final int start, final int offset, final int tabSize) {
    int hostStart = myDocumentWindow.injectedToHost(start);
    int hostOffset = myDocumentWindow.injectedToHost(offset);
    return myDelegate.calcColumnNumber(myDelegate.getDocument().getText(), hostStart, hostOffset, tabSize);
  }

  @Override
  public int calcColumnNumber(int offset, int lineIndex) {
    return myDelegate.calcColumnNumber(myDocumentWindow.injectedToHost(offset), myDocumentWindow.injectedToHostLine(lineIndex));
  }

  @NotNull
  @Override
  public IndentsModel getIndentsModel() {
    return myDelegate.getIndentsModel();
  }

  @Override
  public void setSoftWrapAppliancePlace(@NotNull SoftWrapAppliancePlaces place) {
    myDelegate.setSoftWrapAppliancePlace(place);
  }

  @Override
  public void setPlaceholder(@Nullable CharSequence text) {
    myDelegate.setPlaceholder(text);
  }

  @Override
  public boolean isStickySelection() {
    return myDelegate.isStickySelection();
  }

  @Override
  public void setStickySelection(boolean enable) {
    myDelegate.setStickySelection(enable);
  }

  @Override
  public boolean isPurePaintingMode() {
    return myDelegate.isPurePaintingMode();
  }

  @Override
  public void setPurePaintingMode(boolean enabled) {
    myDelegate.setPurePaintingMode(enabled);
  }

  @Override
  public void registerScrollBarRepaintCallback(@Nullable RepaintCallback callback) {
    myDelegate.registerScrollBarRepaintCallback(callback);
  }

  @Override
  public void setPrefixTextAndAttributes(@Nullable String prefixText, @Nullable TextAttributes attributes) {
    myDelegate.setPrefixTextAndAttributes(prefixText, attributes);
  }

  @Override
  public int getPrefixTextWidthInPixels() {
    return myDelegate.getPrefixTextWidthInPixels();
  }
}
