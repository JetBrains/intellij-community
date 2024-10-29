// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.injected.editor.MarkupModelWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
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
import com.intellij.openapi.editor.highlighter.LightHighlighterClient;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.TextDrawingCallback;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.function.IntFunction;

final class EditorWindowImpl extends UserDataHolderBase implements EditorWindow, EditorEx {
  private final DocumentWindowImpl myDocumentWindow;
  private final EditorImpl myDelegate;
  volatile PsiFile myInjectedFile;
  private final boolean myOneLine;
  private final CaretModelWindow myCaretModelDelegate;
  private final SelectionModelWindow mySelectionModelDelegate;
  private volatile boolean myDisposed;
  private final MarkupModelWindow myMarkupModelDelegate;
  private final MarkupModelWindow myDocumentMarkupModelDelegate;
  private final FoldingModelWindow myFoldingModelWindow;
  private final SoftWrapModelWindow mySoftWrapModel;
  private final InlayModelWindow myInlayModel;

  EditorWindowImpl(@NotNull DocumentWindowImpl documentWindow,
                   final @NotNull EditorImpl delegate,
                   @NotNull PsiFile injectedFile,
                   boolean oneLine) {
    myDocumentWindow = documentWindow;
    myDelegate = delegate;
    myInjectedFile = injectedFile;
    myOneLine = oneLine;
    myCaretModelDelegate = new CaretModelWindow(myDelegate.getCaretModel(), this);
    mySelectionModelDelegate = new SelectionModelWindow(myDelegate, myDocumentWindow, this);
    myMarkupModelDelegate = new MarkupModelWindow(myDelegate.getMarkupModel(), myDocumentWindow);
    myDocumentMarkupModelDelegate = new MarkupModelWindow(myDelegate.getFilteredDocumentMarkupModel(), myDocumentWindow);
    myFoldingModelWindow = new FoldingModelWindow(delegate.getFoldingModel(), documentWindow, this);
    mySoftWrapModel = new SoftWrapModelWindow();
    myInlayModel = new InlayModelWindow();
  }

  @Override
  public boolean isValid() {
    return !isDisposed() && !myInjectedFile.getProject().isDisposed() && myInjectedFile.isValid() && myDocumentWindow.isValid();
  }

  void assertValid() {
    PsiUtilCore.ensureValid(myInjectedFile);
    if (!isValid()) {
      StringBuilder reason = new StringBuilder("Not valid");
      if (myDisposed) reason.append("; editorWindow: disposed");
      if (!myDocumentWindow.isValid()) reason.append("; documentWindow: invalid");
      if (myDelegate.isDisposed()) reason.append("; editor: disposed");
      if (myInjectedFile.getProject().isDisposed()) reason.append("; project: disposed");
      throw new AssertionError(reason.toString());
    }
  }

  @Override
  public @NotNull PsiFile getInjectedFile() {
    return myInjectedFile;
  }

  @Override
  public @NotNull LogicalPosition hostToInjected(@NotNull LogicalPosition hPos) {
    assertValid();
    DocumentEx hostDocument = myDelegate.getDocument();
    int hLineEndOffset =
      hPos.line >= hostDocument.getLineCount() ? hostDocument.getTextLength() : hostDocument.getLineEndOffset(hPos.line);
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

  @Override
  public @NotNull LogicalPosition injectedToHost(@NotNull LogicalPosition pos) {
    assertValid();

    int offset = logicalPositionToOffset(pos);
    LogicalPosition samePos = offsetToLogicalPosition(offset);

    int virtualSpaceDelta = offset < myDocumentWindow.getTextLength() && samePos.line == pos.line && samePos.column < pos.column ?
                            pos.column - samePos.column : 0;

    LogicalPosition hostPos = myDelegate.offsetToLogicalPosition(myDocumentWindow.injectedToHost(offset));
    return new LogicalPosition(hostPos.line, hostPos.column + virtualSpaceDelta);
  }

  void dispose() {
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

    InjectedLanguageUtilBase.clearCaches(myInjectedFile.getProject(), getDocument());
  }

  @Override
  public void setViewer(boolean isViewer) {
    myDelegate.setViewer(isViewer);
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
  public void setFile(final @NotNull VirtualFile vFile) {
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
  public @Nullable JComponent getHeaderComponent() {
    return null;
  }

  @Override
  public TextDrawingCallback getTextDrawingCallback() {
    return myDelegate.getTextDrawingCallback();
  }

  @Override
  public @NotNull SelectionModel getSelectionModel() {
    return mySelectionModelDelegate;
  }

  @Override
  public @NotNull MarkupModelEx getMarkupModel() {
    return myMarkupModelDelegate;
  }

  @Override
  public @NotNull MarkupModelEx getFilteredDocumentMarkupModel() {
    return myDocumentMarkupModelDelegate;
  }

  @Override
  public @NotNull FoldingModelEx getFoldingModel() {
    return myFoldingModelWindow;
  }

  @Override
  public @NotNull CaretModel getCaretModel() {
    return myCaretModelDelegate;
  }

  @Override
  public @NotNull ScrollingModelEx getScrollingModel() {
    return myDelegate.getScrollingModel();
  }

  @Override
  public @NotNull SoftWrapModelEx getSoftWrapModel() {
    return mySoftWrapModel;
  }

  @Override
  public @NotNull EditorSettings getSettings() {
    return myDelegate.getSettings();
  }

  @Override
  public @NotNull InlayModel getInlayModel() {
    return myInlayModel;
  }

  @Override
  public @NotNull EditorKind getEditorKind() {
    return myDelegate.getEditorKind();
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
  public void setFontSize(final float fontSize) {
    myDelegate.setFontSize(fontSize);
  }

  @Override
  public void setHighlighter(final @NotNull EditorHighlighter highlighter) {
    myDelegate.setHighlighter(highlighter);
  }

  @Override
  public @NotNull EditorHighlighter getHighlighter() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    SyntaxHighlighter syntaxHighlighter =
      SyntaxHighlighterFactory.getSyntaxHighlighter(myInjectedFile.getLanguage(), getProject(), myInjectedFile.getVirtualFile());
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(syntaxHighlighter, scheme);
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
  public @NotNull JComponent getContentComponent() {
    return myDelegate.getContentComponent();
  }

  @Override
  public @NotNull EditorGutterComponentEx getGutterComponentEx() {
    return myDelegate.getGutterComponentEx();
  }

  @Override
  public void addPropertyChangeListener(final @NotNull PropertyChangeListener listener) {
    myDelegate.addPropertyChangeListener(listener);
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable parentDisposable) {
    myDelegate.addPropertyChangeListener(listener, parentDisposable);
  }

  @Override
  public void removePropertyChangeListener(final @NotNull PropertyChangeListener listener) {
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
  public @NotNull VisualPosition xyToVisualPosition(final @NotNull Point p) {
    return logicalToVisualPosition(xyToLogicalPosition(p));
  }

  @Override
  public @NotNull VisualPosition xyToVisualPosition(@NotNull Point2D p) {
    assertValid();
    Point2D pp = p.getX() >= 0 && p.getY() >= 0 ? p : new Point2D.Double(Math.max(p.getX(), 0), Math.max(p.getY(), 0));
    LogicalPosition hostPos = myDelegate.visualToLogicalPosition(myDelegate.xyToVisualPosition(pp));
    return logicalToVisualPosition(hostToInjected(hostPos));
  }

  @Override
  public @NotNull VisualPosition offsetToVisualPosition(final int offset) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset));
  }

  @Override
  public @NotNull VisualPosition offsetToVisualPosition(int offset, boolean leanForward, boolean beforeSoftWrap) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset).leanForward(leanForward));
  }

  @Override
  public @NotNull LogicalPosition offsetToLogicalPosition(final int offset) {
    assertValid();
    int lineNumber = myDocumentWindow.getLineNumber(offset);
    int lineStartOffset = myDocumentWindow.getLineStartOffset(lineNumber);
    int column = calcLogicalColumnNumber(offset - lineStartOffset, lineNumber, lineStartOffset);
    return new LogicalPosition(lineNumber, column);
  }

  @Override
  public @NotNull EditorColorsScheme createBoundColorSchemeDelegate(@Nullable EditorColorsScheme customGlobalScheme) {
    return myDelegate.createBoundColorSchemeDelegate(customGlobalScheme);
  }

  @Override
  public @NotNull LogicalPosition xyToLogicalPosition(final @NotNull Point p) {
    assertValid();
    LogicalPosition hostPos = myDelegate.xyToLogicalPosition(p);
    return hostToInjected(hostPos);
  }

  @Override
  public @NotNull Point logicalPositionToXY(final @NotNull LogicalPosition pos) {
    assertValid();
    LogicalPosition hostPos = injectedToHost(pos);
    return myDelegate.logicalPositionToXY(hostPos);
  }

  @Override
  public @NotNull Point visualPositionToXY(final @NotNull VisualPosition pos) {
    assertValid();
    return logicalPositionToXY(visualToLogicalPosition(pos));
  }

  @Override
  public @NotNull Point2D visualPositionToPoint2D(@NotNull VisualPosition pos) {
    assertValid();
    LogicalPosition hostLogical = injectedToHost(visualToLogicalPosition(pos));
    VisualPosition hostVisual = myDelegate.logicalToVisualPosition(hostLogical);
    return myDelegate.visualPositionToPoint2D(hostVisual);
  }

  @Override
  public void repaint(final int startOffset, final int endOffset) {
    assertValid();
    myDelegate.repaint(myDocumentWindow.injectedToHost(startOffset), myDocumentWindow.injectedToHost(endOffset));
  }

  @Override
  public @NotNull DocumentWindowImpl getDocument() {
    return myDocumentWindow;
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myDelegate.getComponent();
  }

  private final ListenerWrapperMap<EditorMouseListener> myEditorMouseListeners = new ListenerWrapperMap<>();

  @Override
  public void addEditorMouseListener(final @NotNull EditorMouseListener listener) {
    assertValid();
    EditorMouseListener wrapper = new EditorMouseListener() {
      @Override
      public void mousePressed(@NotNull EditorMouseEvent e) {
        listener.mousePressed(convertEvent(e));
      }

      @Override
      public void mouseClicked(@NotNull EditorMouseEvent e) {
        listener.mouseClicked(convertEvent(e));
      }

      @Override
      public void mouseReleased(@NotNull EditorMouseEvent e) {
        listener.mouseReleased(convertEvent(e));
      }

      @Override
      public void mouseEntered(@NotNull EditorMouseEvent e) {
        listener.mouseEntered(convertEvent(e));
      }

      @Override
      public void mouseExited(@NotNull EditorMouseEvent e) {
        listener.mouseExited(convertEvent(e));
      }
    };
    myEditorMouseListeners.registerWrapper(listener, wrapper);

    myDelegate.addEditorMouseListener(wrapper);
  }

  @Override
  public void removeEditorMouseListener(final @NotNull EditorMouseListener listener) {
    EditorMouseListener wrapper = myEditorMouseListeners.removeWrapper(listener);
    // HintManager might have an old editor instance
    if (wrapper != null) {
      myDelegate.removeEditorMouseListener(wrapper);
    }
  }

  private final ListenerWrapperMap<EditorMouseMotionListener> myEditorMouseMotionListeners = new ListenerWrapperMap<>();

  @Override
  public void addEditorMouseMotionListener(final @NotNull EditorMouseMotionListener listener) {
    assertValid();
    EditorMouseMotionListener wrapper = new EditorMouseMotionListener() {
      @Override
      public void mouseMoved(@NotNull EditorMouseEvent e) {
        listener.mouseMoved(convertEvent(e));
      }

      @Override
      public void mouseDragged(@NotNull EditorMouseEvent e) {
        listener.mouseDragged(convertEvent(e));
      }
    };
    myEditorMouseMotionListeners.registerWrapper(listener, wrapper);
    myDelegate.addEditorMouseMotionListener(wrapper);
  }

  @Override
  public void removeEditorMouseMotionListener(final @NotNull EditorMouseMotionListener listener) {
    EditorMouseMotionListener wrapper = myEditorMouseMotionListeners.removeWrapper(listener);
    if (wrapper != null) {
      myDelegate.removeEditorMouseMotionListener(wrapper);
    }
  }

  private @NotNull EditorMouseEvent convertEvent(@NotNull EditorMouseEvent originalEvent) {
    LogicalPosition logicalPosition = hostToInjected(originalEvent.getLogicalPosition());
    int offset = logicalPositionToOffset(logicalPosition);
    VisualPosition visualPosition = logicalToVisualPosition(logicalPosition);
    FoldRegion hostFoldRegion = originalEvent.getCollapsedFoldRegion();
    return new EditorMouseEvent(this, originalEvent.getMouseEvent(), originalEvent.getArea(),
                                offset, logicalPosition, visualPosition, true,
                                hostFoldRegion == null ? null : FoldingRegionWindow.getInjectedRegion(hostFoldRegion), null, null);
  }

  @Override
  public boolean isDisposed() {
    return myDisposed || myDelegate.isDisposed();
  }

  @Override
  public void setBackgroundColor(final Color color) {
    myDelegate.setBackgroundColor(color);
  }

  @Override
  public @NotNull Color getBackgroundColor() {
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

  @Override
  public @NotNull JScrollPane getScrollPane() {
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
  public int logicalPositionToOffset(final @NotNull LogicalPosition pos) {
    int lineStartOffset = myDocumentWindow.getLineStartOffset(pos.line);
    return calcOffset(pos.column, pos.line, lineStartOffset);
  }

  private int calcLogicalColumnNumber(int offsetInLine, int lineNumber, int lineStartOffset) {
    if (myDocumentWindow.getTextLength() == 0) return 0;

    if (offsetInLine == 0) return 0;
    int end = myDocumentWindow.getLineEndOffset(lineNumber);
    if (offsetInLine > end - lineStartOffset) offsetInLine = end - lineStartOffset;

    CharSequence text = myDocumentWindow.getCharsSequence();
    return EditorUtil.calcColumnNumber(this, text, lineStartOffset, lineStartOffset + offsetInLine);
  }

  private int calcOffset(int col, int lineNumber, int lineStartOffset) {
    CharSequence text = myDocumentWindow.getImmutableCharSequence();
    int tabSize = EditorUtil.getTabSize(myDelegate);
    int end = myDocumentWindow.getLineEndOffset(lineNumber);
    int currentColumn = 0;
    for (int i = lineStartOffset; i < end; i++) {
      char c = text.charAt(i);
      if (c == '\t') {
        currentColumn = (currentColumn / tabSize + 1) * tabSize;
      }
      else {
        currentColumn++;
      }
      if (col < currentColumn) return i;
    }
    return end;
  }

  // assuming there is no folding in injected documents
  @Override
  public @NotNull VisualPosition logicalToVisualPosition(final @NotNull LogicalPosition pos) {
    assertValid();
    return new VisualPosition(pos.line, pos.column);
  }

  @Override
  public @NotNull LogicalPosition visualToLogicalPosition(final @NotNull VisualPosition pos) {
    assertValid();
    return new LogicalPosition(pos.line, pos.column);
  }

  @Override
  public @NotNull DataContext getDataContext() {
    return myDelegate.getDataContext();
  }

  @Override
  public EditorMouseEventArea getMouseEventArea(final @NotNull MouseEvent e) {
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
  public void addFocusListener(final @NotNull FocusChangeListener listener) {
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
  public @NotNull CopyProvider getCopyProvider() {
    return myDelegate.getCopyProvider();
  }

  @Override
  public @NotNull CutProvider getCutProvider() {
    return myDelegate.getCutProvider();
  }

  @Override
  public @NotNull PasteProvider getPasteProvider() {
    return myDelegate.getPasteProvider();
  }

  @Override
  public @NotNull DeleteProvider getDeleteProvider() {
    return myDelegate.getDeleteProvider();
  }

  @Override
  public void setColorsScheme(final @NotNull EditorColorsScheme scheme) {
    myDelegate.setColorsScheme(scheme);
  }

  @Override
  public @NotNull EditorColorsScheme getColorsScheme() {
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
  public boolean processKeyTyped(final @NotNull KeyEvent e) {
    return myDelegate.processKeyTyped(e);
  }

  @Override
  public @NotNull EditorGutter getGutter() {
    return myDelegate.getGutter();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final EditorWindowImpl that = (EditorWindowImpl)o;

    DocumentWindow thatWindow = that.getDocument();
    return myDelegate.equals(that.myDelegate) && myDocumentWindow.equals(thatWindow);
  }

  @Override
  public int hashCode() {
    return myDocumentWindow.hashCode();
  }

  @Override
  public @NotNull Editor getDelegate() {
    return myDelegate;
  }

  @Override
  public @NotNull IndentsModel getIndentsModel() {
    return myDelegate.getIndentsModel();
  }

  @Override
  public void setPlaceholder(@Nullable CharSequence text) {
    myDelegate.setPlaceholder(text);
  }

  @Override
  public void setPlaceholderAttributes(@Nullable TextAttributes attributes) {
    myDelegate.setPlaceholderAttributes(attributes);
  }

  @Override
  public void setShowPlaceholderWhenFocused(boolean show) {
    myDelegate.setShowPlaceholderWhenFocused(show);
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
  public void registerLineExtensionPainter(@NotNull IntFunction<? extends @NotNull Collection<? extends LineExtensionInfo>> lineExtensionPainter) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void registerScrollBarRepaintCallback(@Nullable ButtonlessScrollBarUI.ScrollbarRepaintCallback callback) {
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

  @Override
  public String toString() {
    return super.toString() + "[disposed=" + myDisposed + "; valid=" + isValid() + "]";
  }

  @Override
  public int getExpectedCaretOffset() {
    return myDocumentWindow.hostToInjected(myDelegate.getExpectedCaretOffset());
  }

  @Override
  public void setContextMenuGroupId(@Nullable String groupId) {
    myDelegate.setContextMenuGroupId(groupId);
  }

  @Override
  public @Nullable String getContextMenuGroupId() {
    return myDelegate.getContextMenuGroupId();
  }

  @Override
  public void installPopupHandler(@NotNull EditorPopupHandler popupHandler) {
    myDelegate.installPopupHandler(popupHandler);
  }

  @Override
  public void uninstallPopupHandler(@NotNull EditorPopupHandler popupHandler) {
    myDelegate.installPopupHandler(popupHandler);
  }

  @Override
  public @Nullable ActionGroup getPopupActionGroup(@NotNull EditorMouseEvent event) {
    return myDelegate.getPopupActionGroup(event);
  }

  @Override
  public void setCustomCursor(@NotNull Object requestor, @Nullable Cursor cursor) {
    myDelegate.setCustomCursor(requestor, cursor);
  }

  @Override
  public int getAscent() {
    return myDelegate.getAscent();
  }
}
