// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.side;

import com.intellij.diff.DiffContext;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.holders.EditorHolder;
import com.intellij.diff.tools.holders.EditorHolderFactory;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.DiffTitleHandler;
import com.intellij.diff.tools.util.SimpleDiffPanel;
import com.intellij.diff.tools.util.base.ListenerDiffViewerBase;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.Navigatable;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class OnesideDiffViewer<T extends EditorHolder> extends ListenerDiffViewerBase {
  protected final @NotNull SimpleDiffPanel myPanel;
  @ApiStatus.Internal protected final @NotNull OnesideContentPanel myContentPanel;

  private final @NotNull Side mySide;
  private final @NotNull T myHolder;

  public OnesideDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request, @NotNull EditorHolderFactory<T> factory) {
    super(context, request);

    mySide = Side.fromRight(myRequest.getContents().get(0) instanceof EmptyContent);
    myHolder = createEditorHolder(factory);

    myContentPanel = OnesideContentPanel.createFromHolder(myHolder);

    myPanel = new SimpleDiffPanel(myContentPanel, context) {
      @Override
      public void uiDataSnapshot(@NotNull DataSink sink) {
        super.uiDataSnapshot(sink);
        DataSink.uiDataSnapshot(sink, OnesideDiffViewer.this);
      }
    };
  }

  @Override
  protected void onInit() {
    super.onInit();
    myPanel.setPersistentNotifications(DiffUtil.createCustomNotifications(this, myContext, myRequest));
    DiffTitleHandler.createHandler(() -> createTitle(), myContentPanel, myRequest, this);
  }

  @Override
  @RequiresEdt
  protected void onDispose() {
    destroyEditorHolder();
    super.onDispose();
  }

  //
  // Editors
  //

  protected @NotNull T createEditorHolder(@NotNull EditorHolderFactory<T> factory) {
    DiffContent content = mySide.select(myRequest.getContents());
    return factory.create(content, myContext);
  }

  private void destroyEditorHolder() {
    Disposer.dispose(myHolder);
  }

  protected @Nullable JComponent createTitle() {
    List<JComponent> simpleTitles = DiffUtil.createSimpleTitles(this, myRequest);
    return getSide().select(simpleTitles);
  }

  //
  // Getters
  //

  @Override
  public @NotNull JComponent getComponent() {
    return myPanel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    if (!myPanel.isGoodContent()) return null;
    return getEditorHolder().getPreferredFocusedComponent();
  }

  public @NotNull Side getSide() {
    return mySide;
  }

  protected @NotNull DiffContent getContent() {
    return mySide.select(myRequest.getContents());
  }

  protected @NotNull T getEditorHolder() {
    return myHolder;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(DiffDataKeys.CURRENT_CONTENT, getContent());
  }

  //
  // Misc
  //

  @Override
  public @Nullable Navigatable getNavigatable() {
    return getContent().getNavigatable();
  }

  public static <T extends EditorHolder> boolean canShowRequest(@NotNull DiffContext context,
                                                                @NotNull DiffRequest request,
                                                                @NotNull EditorHolderFactory<T> factory) {
    if (!(request instanceof ContentDiffRequest)) return false;

    List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
    if (contents.size() != 2) return false;

    DiffContent content1 = contents.get(0);
    DiffContent content2 = contents.get(1);

    if (content1 instanceof EmptyContent) {
      return factory.canShowContent(content2, context) && factory.wantShowContent(content2, context);
    }
    if (content2 instanceof EmptyContent) {
      return factory.canShowContent(content1, context) && factory.wantShowContent(content1, context);
    }
    return false;
  }
}
