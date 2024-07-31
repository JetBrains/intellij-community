/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class OnesideDiffViewer<T extends EditorHolder> extends ListenerDiffViewerBase {
  @NotNull protected final SimpleDiffPanel myPanel;
  @NotNull protected final OnesideContentPanel myContentPanel;

  @NotNull private final Side mySide;
  @NotNull private final T myHolder;

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

  @NotNull
  protected T createEditorHolder(@NotNull EditorHolderFactory<T> factory) {
    DiffContent content = mySide.select(myRequest.getContents());
    return factory.create(content, myContext);
  }

  private void destroyEditorHolder() {
    Disposer.dispose(myHolder);
  }

  @Nullable
  protected JComponent createTitle() {
    List<JComponent> simpleTitles = DiffUtil.createSimpleTitles(this, myRequest);
    return getSide().select(simpleTitles);
  }

  //
  // Getters
  //

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    if (!myPanel.isGoodContent()) return null;
    return getEditorHolder().getPreferredFocusedComponent();
  }

  @NotNull
  public Side getSide() {
    return mySide;
  }

  @NotNull
  protected DiffContent getContent() {
    return mySide.select(myRequest.getContents());
  }

  @NotNull
  protected T getEditorHolder() {
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

  @Nullable
  @Override
  public Navigatable getNavigatable() {
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
