package com.intellij.injected.editor;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author cdr
 */
public class FoldingModelWindow implements FoldingModelEx{
  private final FoldingModelEx myDelegate;
  private final DocumentWindow myDocumentWindow;

  public FoldingModelWindow(FoldingModelEx delegate, DocumentWindow documentWindow) {
    myDelegate = delegate;
    myDocumentWindow = documentWindow;
  }

  public void setFoldingEnabled(boolean isEnabled) {
    myDelegate.setFoldingEnabled(isEnabled);
  }

  public boolean isFoldingEnabled() {
    return myDelegate.isFoldingEnabled();
  }

  public FoldRegion getFoldingPlaceholderAt(Point p) {
    return myDelegate.getFoldingPlaceholderAt(p);
  }

  public FoldRegion[] getAllFoldRegionsIncludingInvalid() {
    return myDelegate.getAllFoldRegionsIncludingInvalid();
  }

  public boolean intersectsRegion(int startOffset, int endOffset) {
    int hostStart = myDocumentWindow.injectedToHost(startOffset);
    int hostEnd = myDocumentWindow.injectedToHost(endOffset);
    return myDelegate.intersectsRegion(hostStart, hostEnd);
  }

  public FoldRegion addFoldRegion(int startOffset, int endOffset, @NotNull String placeholderText) {
    return myDelegate.addFoldRegion(myDocumentWindow.injectedToHost(startOffset), myDocumentWindow.injectedToHost(endOffset), placeholderText);
  }

  public void removeFoldRegion(FoldRegion region) {
    myDelegate.removeFoldRegion(region);
  }

  @NotNull
  public FoldRegion[] getAllFoldRegions() {
    return myDelegate.getAllFoldRegions();
  }

  public boolean isOffsetCollapsed(int offset) {
    return myDelegate.isOffsetCollapsed(myDocumentWindow.injectedToHost(offset));
  }

  public FoldRegion getCollapsedRegionAtOffset(int offset) {
    return myDelegate.getCollapsedRegionAtOffset(myDocumentWindow.injectedToHost(offset));
  }

  public void runBatchFoldingOperation(Runnable operation) {
    myDelegate.runBatchFoldingOperation(operation);
  }

  public void runBatchFoldingOperationDoNotCollapseCaret(Runnable operation) {
    myDelegate.runBatchFoldingOperationDoNotCollapseCaret(operation);
  }
}
