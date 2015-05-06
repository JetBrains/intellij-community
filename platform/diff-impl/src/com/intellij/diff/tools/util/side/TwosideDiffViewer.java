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
import com.intellij.diff.tools.util.FocusTrackerSupport;
import com.intellij.diff.tools.util.SimpleDiffPanel;
import com.intellij.diff.tools.util.base.ListenerDiffViewerBase;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class TwosideDiffViewer<T extends EditorHolder> extends ListenerDiffViewerBase {
  @NotNull protected final SimpleDiffPanel myPanel;
  @NotNull protected final TwosideContentPanel myContentPanel;

  @NotNull private final List<T> myHolders;
  @NotNull private final List<? extends DiffContent> myActualContents;
  @NotNull private final FocusTrackerSupport.TwosideFocusTrackerSupport myFocusTrackerSupport;

  public TwosideDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request, @NotNull EditorHolderFactory<T> factory) {
    super(context, (ContentDiffRequest)request);

    myActualContents = ContainerUtil.map(myRequest.getContents(), new Function<DiffContent, DiffContent>() {
      @Override
      public DiffContent fun(DiffContent content) {
        return content instanceof EmptyContent ? null : content;
      }
    });

    myHolders = createEditorHolders(factory);
    assert myHolders.get(0) != null || myHolders.get(1) != null;

    List<JComponent> titlePanels = createTitles();
    myFocusTrackerSupport = new FocusTrackerSupport.TwosideFocusTrackerSupport(myHolders);
    myContentPanel = new TwosideContentPanel(myHolders, titlePanels);

    myPanel = new SimpleDiffPanel(myContentPanel, this, context);
  }

  @Override
  @CalledInAwt
  protected void onDispose() {
    destroyEditorHolders();
    super.onDispose();
  }

  @Override
  @CalledInAwt
  protected void processContextHints() {
    super.processContextHints();
    myFocusTrackerSupport.processContextHints(myRequest, myContext);
  }

  @Override
  @CalledInAwt
  protected void updateContextHints() {
    super.updateContextHints();
    myFocusTrackerSupport.updateContextHints(myRequest, myContext);
  }

  //
  // Editors
  //

  @NotNull
  protected List<T> createEditorHolders(@NotNull EditorHolderFactory<T> factory) {
    List<T> holders = new ArrayList<T>(2);
    for (int i = 0; i < 2; i++) {
      DiffContent content = myActualContents.get(i);
      holders.add(content == null ? null : factory.create(content, myContext));
    }
    return holders;
  }

  private void destroyEditorHolders() {
    for (T holder : myHolders) {
      if (holder != null) Disposer.dispose(holder);
    }
  }

  @NotNull
  protected List<JComponent> createTitles() {
    return DiffUtil.createSimpleTitles(myRequest);
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
  protected Side getCurrentSide() {
    return myFocusTrackerSupport.getCurrentSide();
  }

  protected void setCurrentSide(@NotNull Side side) {
    myFocusTrackerSupport.setCurrentSide(side);
  }

  @NotNull
  protected List<T> getEditorHolders() {
    return myHolders;
  }

  @NotNull
  protected List<? extends DiffContent> getActualContents() {
    return myActualContents;
  }

  @NotNull
  protected T getCurrentEditorHolder() {
    //noinspection ConstantConditions
    return getCurrentSide().select(myHolders);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return DiffUtil.getVirtualFile(myRequest, getCurrentSide());
    }
    else if (DiffDataKeys.CURRENT_CONTENT.is(dataId)) {
      return getCurrentSide().select(getActualContents());
    }
    return super.getData(dataId);
  }

  //
  // Misc
  //

  @Nullable
  @Override
  protected OpenFileDescriptor getOpenFileDescriptor() {
    return getCurrentSide().selectNotNull(getRequest().getContents()).getOpenFileDescriptor();
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
      canShow &= content instanceof EmptyContent || factory.canShowContent(content, context);
      wantShow |= factory.wantShowContent(content, context);
    }
    return canShow && wantShow;
  }
}
