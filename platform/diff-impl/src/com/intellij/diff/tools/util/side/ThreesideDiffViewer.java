// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.side;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.holders.EditorHolder;
import com.intellij.diff.tools.holders.EditorHolderFactory;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.DiffTitleHandler;
import com.intellij.diff.tools.util.FocusTrackerSupport;
import com.intellij.diff.tools.util.SimpleDiffPanel;
import com.intellij.diff.tools.util.base.ListenerDiffViewerBase;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.ThreeSide;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.Navigatable;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class ThreesideDiffViewer<T extends EditorHolder> extends ListenerDiffViewerBase {
  @NotNull protected final SimpleDiffPanel myPanel;
  @NotNull protected final ThreesideContentPanel myContentPanel;
  @NotNull protected final JBLoadingPanel myLoadingPanel;

  @NotNull private final List<T> myHolders;

  @NotNull private final FocusTrackerSupport<ThreeSide> myFocusTrackerSupport;

  public ThreesideDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request, @NotNull EditorHolderFactory<T> factory) {
    super(context, request);

    myHolders = createEditorHolders(factory);

    myFocusTrackerSupport = new FocusTrackerSupport.Threeside(myHolders);
    myContentPanel = new ThreesideContentPanel.Holders(myHolders);

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), this, 300);
    myLoadingPanel.add(myContentPanel, BorderLayout.CENTER);

    myPanel = new SimpleDiffPanel(myLoadingPanel, context) {
      @Override
      public void uiDataSnapshot(@NotNull DataSink sink) {
        super.uiDataSnapshot(sink);
        DataSink.uiDataSnapshot(sink, ThreesideDiffViewer.this);
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
  }

  @Override
  @RequiresEdt
  protected void updateContextHints() {
    super.updateContextHints();
    myFocusTrackerSupport.updateContextHints(myRequest, myContext);
  }

  @NotNull
  protected List<T> createEditorHolders(@NotNull EditorHolderFactory<T> factory) {
    List<DiffContent> contents = myRequest.getContents();

    List<T> holders = new ArrayList<>(3);
    for (int i = 0; i < 3; i++) {
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
  public ThreeSide getCurrentSide() {
    return myFocusTrackerSupport.getCurrentSide();
  }

  protected void setCurrentSide(@NotNull ThreeSide side) {
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
    return getCurrentSide().select(getRequest().getContents()).getNavigatable();
  }

  public static <T extends EditorHolder> boolean canShowRequest(@NotNull DiffContext context,
                                                                @NotNull DiffRequest request,
                                                                @NotNull EditorHolderFactory<T> factory) {
    if (!(request instanceof ContentDiffRequest)) return false;

    List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
    if (contents.size() != 3) return false;

    boolean canShow = true;
    boolean wantShow = false;
    for (DiffContent content : contents) {
      canShow &= factory.canShowContent(content, context);
      wantShow |= factory.wantShowContent(content, context);
    }
    return canShow && wantShow;
  }

  //
  // Actions
  //

  protected enum PartialDiffMode {LEFT_MIDDLE, RIGHT_MIDDLE, MIDDLE_LEFT, MIDDLE_RIGHT, LEFT_RIGHT}
  protected class ShowPartialDiffAction extends DumbAwareAction {
    @NotNull protected final ThreeSide mySide1;
    @NotNull protected final ThreeSide mySide2;

    public ShowPartialDiffAction(@NotNull PartialDiffMode mode, boolean hasFourSides) {
      String id;
      Icon icon = null;
      switch (mode) {
        case LEFT_MIDDLE -> {
          mySide1 = ThreeSide.LEFT;
          mySide2 = ThreeSide.BASE;
          id = "Diff.ComparePartial.Base.Left";
          if (!hasFourSides) icon = AllIcons.Diff.Compare3LeftMiddle;
        }
        case RIGHT_MIDDLE -> {
          mySide1 = ThreeSide.RIGHT;
          mySide2 = ThreeSide.BASE;
          id = "Diff.ComparePartial.Base.Right";
          if (!hasFourSides) icon = AllIcons.Diff.Compare3MiddleRight;
        }
        case MIDDLE_LEFT -> {
          mySide1 = ThreeSide.BASE;
          mySide2 = ThreeSide.LEFT;
          id = "Diff.ComparePartial.Base.Left";
          if (!hasFourSides) icon = AllIcons.Diff.Compare3LeftMiddle;
        }
        case MIDDLE_RIGHT -> {
          mySide1 = ThreeSide.BASE;
          mySide2 = ThreeSide.RIGHT;
          id = "Diff.ComparePartial.Base.Right";
          if (!hasFourSides) icon = AllIcons.Diff.Compare3MiddleRight;
        }
        case LEFT_RIGHT -> {
          mySide1 = ThreeSide.LEFT;
          mySide2 = ThreeSide.RIGHT;
          id = "Diff.ComparePartial.Left.Right";
          if (!hasFourSides) icon = AllIcons.Diff.Compare3LeftRight;
        }
        default -> throw new IllegalArgumentException();
      }
      String text = ActionsBundle.message("action.Diff.ComparePartial.Generic", mySide1.getIndex(), mySide2.getIndex());
      getTemplatePresentation().setText(text);
      getTemplatePresentation().setIcon(icon);

      ActionUtil.mergeFrom(this, id);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      DiffRequest request = createRequest();
      DiffManager.getInstance().showDiff(myProject, request, new DiffDialogHints(null, myPanel));
    }

    @NotNull
    protected SimpleDiffRequest createRequest() {
      List<DiffContent> contents = myRequest.getContents();
      List<String> titles = myRequest.getContentTitles();
      return new SimpleDiffRequest(myRequest.getTitle(),
                                   mySide1.select(contents), mySide2.select(contents),
                                   mySide1.select(titles), mySide2.select(titles));
    }
  }
}
