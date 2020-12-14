// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.merge;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.ui.WindowWrapperBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class MergeWindow {
  private static final Logger LOG = Logger.getInstance(MergeWindow.class);

  @Nullable private final Project myProject;
  @NotNull private final DiffDialogHints myHints;

  private MergeRequestProcessor myProcessor;
  private WindowWrapper myWrapper;

  public MergeWindow(@Nullable Project project, @NotNull DiffDialogHints hints) {
    myProject = project;
    myHints = hints;
  }

  protected void init() {
    if (myWrapper != null) return;

    myProcessor = createProcessor();

    String dialogGroupKey = myProcessor.getContextUserData(DiffUserDataKeys.DIALOG_GROUP_KEY);
    if (dialogGroupKey == null) dialogGroupKey = "MergeDialog";

    myWrapper = new WindowWrapperBuilder(DiffUtil.getWindowMode(myHints), new MyPanel(myProcessor.getComponent()))
      .setProject(myProject)
      .setParent(myHints.getParent())
      .setDimensionServiceKey(dialogGroupKey)
      .setPreferredFocusedComponent(() -> myProcessor.getPreferredFocusedComponent())
      .setOnShowCallback(() -> initProcessor(myProcessor))
      .setOnCloseHandler(() -> myProcessor.checkCloseAction())
      .build();
    myWrapper.setImages(DiffUtil.DIFF_FRAME_ICONS.getValue());
    Disposer.register(myWrapper, myProcessor);

    Consumer<WindowWrapper> wrapperHandler = myHints.getWindowConsumer();
    if (wrapperHandler != null) wrapperHandler.consume(myWrapper);
  }

  public void show() {
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      LOG.error("Merge dialog should not be shown under a write action, as it will disable any background activity.");
    }

    init();
    myWrapper.show();
  }

  @NotNull
  private MergeRequestProcessor createProcessor() {
    return new MergeRequestProcessor(myProject) {
      @Override
      public void closeDialog() {
        myWrapper.close();
      }

      @Override
      protected void setWindowTitle(@NotNull @NlsContexts.DialogTitle String title) {
        myWrapper.setTitle(title);
      }

      @Nullable
      @Override
      protected JRootPane getRootPane() {
        RootPaneContainer container = ObjectUtils.tryCast(myWrapper.getWindow(), RootPaneContainer.class);
        return container != null ? container.getRootPane() : null;
      }
    };
  }

  protected abstract void initProcessor(@NotNull MergeRequestProcessor processor);

  private static class MyPanel extends JPanel {
    MyPanel(@NotNull JComponent content) {
      super(new BorderLayout());
      add(content, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension windowSize = DiffUtil.getDefaultDiffWindowSize();
      Dimension size = super.getPreferredSize();
      return new Dimension(Math.max(windowSize.width, size.width), Math.max(windowSize.height, size.height));
    }
  }

  public static class ForRequest extends MergeWindow {
    @NotNull private final MergeRequest myMergeRequest;

    public ForRequest(@Nullable Project project, @NotNull MergeRequest mergeRequest, @NotNull DiffDialogHints hints) {
      super(project, hints);
      myMergeRequest = mergeRequest;
    }


    @Override
    protected void initProcessor(@NotNull MergeRequestProcessor processor) {
      processor.init(myMergeRequest);
    }
  }

  public static class ForProducer extends MergeWindow {
    @NotNull private final MergeRequestProducer myMergeRequestProducer;

    public ForProducer(@Nullable Project project, @NotNull MergeRequestProducer mergeRequestProducer, @NotNull DiffDialogHints hints) {
      super(project, hints);
      myMergeRequestProducer = mergeRequestProducer;
    }


    @Override
    protected void initProcessor(@NotNull MergeRequestProcessor processor) {
      processor.init(myMergeRequestProducer);
    }
  }
}
