// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.impl;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.ide.FrameStateListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.ui.WindowWrapperBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class DiffWindowBase {

  @NotNull @NonNls public static final String DEFAULT_DIALOG_GROUP_KEY = "DiffContextDialog";

  @Nullable protected final Project myProject;
  @NotNull protected final DiffDialogHints myHints;

  private DiffRequestProcessor myProcessor;
  private WindowWrapper myWrapper;

  public DiffWindowBase(@Nullable Project project, @NotNull DiffDialogHints hints) {
    myProject = project;
    myHints = hints;
  }

  protected void init() {
    if (myWrapper != null) return;

    myProcessor = createProcessor();

    String dialogGroupKey = ObjectUtils
      .notNull(myProcessor.getContextUserData(DiffUserDataKeys.DIALOG_GROUP_KEY), DEFAULT_DIALOG_GROUP_KEY);

    myWrapper = new WindowWrapperBuilder(DiffUtil.getWindowMode(myHints), new MyPanel(myProcessor.getComponent()))
      .setProject(myProject)
      .setParent(myHints.getParent())
      .setDimensionServiceKey(dialogGroupKey)
      .setPreferredFocusedComponent(() -> myProcessor.getPreferredFocusedComponent())
      .setOnShowCallback(() -> myProcessor.updateRequest())
      .build();
    myWrapper.setImages(DiffUtil.DIFF_FRAME_ICONS.getValue());
    Disposer.register(myWrapper, myProcessor);

    Consumer<WindowWrapper> wrapperHandler = myHints.getWindowConsumer();
    if (wrapperHandler != null) wrapperHandler.consume(myWrapper);

    MessageBusConnection appConnection = ApplicationManager.getApplication().getMessageBus().connect(myProcessor);
    appConnection.subscribe(FrameStateListener.TOPIC, new FrameStateListener() {
      @Override
      public void onFrameActivated() {
        DiffRequest request = myProcessor.getActiveRequest();
        if (request != null) {
          VirtualFile[] files = VfsUtilCore.toVirtualFileArray(request.getFilesToRefresh());
          DiffUtil.refreshOnFrameActivation(files);
        }
      }
    });
  }

  public void show() {
    init();
    myWrapper.show();
  }

  @NotNull
  protected abstract DiffRequestProcessor createProcessor();

  //
  // Getters
  //

  protected WindowWrapper getWrapper() {
    return myWrapper;
  }

  protected DiffRequestProcessor getProcessor() {
    return myProcessor;
  }

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
}
