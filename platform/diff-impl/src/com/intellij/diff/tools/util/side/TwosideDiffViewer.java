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
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.holders.EditorHolder;
import com.intellij.diff.tools.holders.EditorHolderFactory;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.DiffTitleHandler;
import com.intellij.diff.tools.util.FocusTrackerSupport;
import com.intellij.diff.tools.util.SimpleDiffPanel;
import com.intellij.diff.tools.util.base.ListenerDiffViewerBase;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.Navigatable;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class TwosideDiffViewer<T extends EditorHolder> extends ListenerDiffViewerBase {
  @NotNull protected final SimpleDiffPanel myPanel;
  @NotNull protected final TwosideContentPanel myContentPanel;

  @NotNull private final List<T> myHolders;

  @NotNull private final FocusTrackerSupport<Side> myFocusTrackerSupport;

  public TwosideDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request, @NotNull EditorHolderFactory<T> factory) {
    super(context, request);

    myHolders = createEditorHolders(factory);

    myFocusTrackerSupport = new FocusTrackerSupport.Twoside(myHolders);
    myContentPanel = TwosideContentPanel.createFromHolders(myHolders);

    myPanel = new SimpleDiffPanel(myContentPanel, context) {
      @Override
      public void uiDataSnapshot(@NotNull DataSink sink) {
        super.uiDataSnapshot(sink);
        DataSink.uiDataSnapshot(sink, TwosideDiffViewer.this);
      }
    };
  }

  @Override
  protected void onInit() {
    super.onInit();
    myPanel.setPersistentNotifications(DiffUtil.createCustomNotifications(this, myContext, myRequest));
    DiffTitleHandler.createHandler(() -> createTitles(), myContentPanel, myRequest, this);
  }

  @Override
  @RequiresEdt
  protected void onDispose() {
    destroyEditorHolders();
    super.onDispose();
  }

  @Override
  @RequiresEdt
  protected void processContextHints() {
    super.processContextHints();
    myFocusTrackerSupport.processContextHints(myRequest, myContext);

    Float proportion = myContext.getUserData(DiffUserDataKeysEx.TWO_SIDE_SPLITTER_PROPORTION);
    if (proportion != null && proportion >= 0.05 && proportion <= 0.95) myContentPanel.getSplitter().setProportion(proportion);
  }

  @Override
  @RequiresEdt
  protected void updateContextHints() {
    super.updateContextHints();
    myFocusTrackerSupport.updateContextHints(myRequest, myContext);

    float proportion = myContentPanel.getSplitter().getProportion();
    myContext.putUserData(DiffUserDataKeysEx.TWO_SIDE_SPLITTER_PROPORTION, proportion);
  }

  //
  // Editors
  //

  @NotNull
  protected List<T> createEditorHolders(@NotNull EditorHolderFactory<T> factory) {
    List<DiffContent> contents = myRequest.getContents();

    List<T> holders = new ArrayList<>(2);
    for (int i = 0; i < 2; i++) {
      DiffContent content = contents.get(i);
      holders.add(factory.create(content, myContext));
    }
    return holders;
  }

  private void destroyEditorHolders() {
    for (T holder : myHolders) {
      Disposer.dispose(holder);
    }
  }

  @NotNull
  protected List<JComponent> createTitles() {
    return DiffUtil.createSimpleTitles(this, myRequest);
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
    return getCurrentEditorHolder().getPreferredFocusedComponent();
  }

  @NotNull
  public Side getCurrentSide() {
    return myFocusTrackerSupport.getCurrentSide();
  }

  public void setCurrentSide(@NotNull Side side) {
    myFocusTrackerSupport.setCurrentSide(side);
  }

  @NotNull
  protected List<T> getEditorHolders() {
    return myHolders;
  }

  @NotNull
  protected T getCurrentEditorHolder() {
    return getCurrentSide().select(getEditorHolders());
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(DiffDataKeys.CURRENT_CONTENT, getCurrentSide().select(myRequest.getContents()));
  }

  //
  // Misc
  //

  @Nullable
  @Override
  public Navigatable getNavigatable() {
    Navigatable navigatable1 = getCurrentSide().select(getRequest().getContents()).getNavigatable();
    if (navigatable1 != null) return navigatable1;
    return getCurrentSide().other().select(getRequest().getContents()).getNavigatable();
  }

  public static <T extends EditorHolder> boolean canShowRequest(@NotNull DiffContext context,
                                                                @NotNull DiffRequest request,
                                                                @NotNull EditorHolderFactory<T> factory) {
    if (!(request instanceof ContentDiffRequest)) return false;

    List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
    if (contents.size() != 2) return false;

    boolean canShow = true;
    boolean wantShow = false;
    for (DiffContent content : contents) {
      canShow &= factory.canShowContent(content, context);
      wantShow |= factory.wantShowContent(content, context);
    }
    return canShow && wantShow;
  }
}
