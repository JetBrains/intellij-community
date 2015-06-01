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
package com.intellij.diff.merge;

import com.intellij.diff.DiffContext;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

public class MergeUtil {
  @NotNull
  public static JComponent createAcceptActionsPanel(@NotNull AcceptActionProcessor processor, @Nullable JComponent component) {
    Action left = SimpleAcceptAction.create(MergeResult.LEFT, processor);
    Action right = SimpleAcceptAction.create(MergeResult.RIGHT, processor);
    Action apply = SimpleAcceptAction.create(MergeResult.RESOLVED, processor);
    Action cancel = SimpleAcceptAction.create(MergeResult.CANCEL, processor);
    if (apply != null) apply.putValue(DialogWrapper.DEFAULT_ACTION, Boolean.TRUE);
    return createAcceptActionsPanel(left, right, apply, cancel, component);
  }

  public static abstract class AcceptActionProcessor {
    public abstract boolean isEnabled(@NotNull MergeResult result);

    public abstract boolean isVisible(@NotNull MergeResult result);

    public abstract void perform(@NotNull MergeResult result);
  }

  private static class SimpleAcceptAction extends AbstractAction {
    @NotNull private final MergeResult myResult;
    @NotNull private final AcceptActionProcessor myProcessor;

    public SimpleAcceptAction(@NotNull MergeResult result, @NotNull AcceptActionProcessor processor) {
      super(getAcceptActionTitle(result));
      myResult = result;
      myProcessor = processor;
    }

    @Override
    public boolean isEnabled() {
      return myProcessor.isEnabled(myResult);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myProcessor.perform(myResult);
    }

    public static SimpleAcceptAction create(@NotNull MergeResult result, @NotNull AcceptActionProcessor acceptActionProcessor) {
      return acceptActionProcessor.isVisible(result) ? new SimpleAcceptAction(result, acceptActionProcessor) : null;
    }
  }

  @NotNull
  public static JComponent createAcceptActionsPanel(@Nullable Action acceptLeft,
                                                    @Nullable Action acceptRight,
                                                    @Nullable Action apply,
                                                    @Nullable Action cancel,
                                                    @Nullable JComponent component) {
    JPanel panel = new JPanel();
    BoxLayout boxLayout = new BoxLayout(panel, BoxLayout.X_AXIS);
    panel.setLayout(boxLayout);

    List<Action> leftActions = ContainerUtil.list(acceptLeft, acceptRight);
    List<Action> rightActions = SystemInfo.isMac ?
                                ContainerUtil.list(cancel, apply) :
                                ContainerUtil.list(apply, cancel);


    for (Action action : leftActions) {
      if (action != null) panel.add(createActionButton(action, component));
    }

    panel.add(Box.createGlue());

    for (Action action : rightActions) {
      if (action != null) panel.add(createActionButton(action, component));
    }

    return panel;
  }

  @NotNull
  public static JButton createActionButton(@NotNull Action action, @Nullable JComponent component) {
    JButton button = new JButton(action);
    if (SystemInfo.isMac) {
      button.putClientProperty("JButton.buttonType", "text");
    }
    if (action.getValue(DialogWrapper.DEFAULT_ACTION) == Boolean.TRUE) {
      Window window = component != null ? UIUtil.getWindow(component) : null;
      if (window instanceof RootPaneContainer) ((RootPaneContainer)window).getRootPane().setDefaultButton(button);
    }
    return button;
  }

  @NotNull
  public static String getAcceptActionTitle(@NotNull MergeResult result) {
    switch (result) {
      case CANCEL:
        return "Abort";
      case LEFT:
        return "Accept Left";
      case RIGHT:
        return "Accept Right";
      case RESOLVED:
        return "Apply";
      default:
        throw new IllegalArgumentException(result.toString());
    }
  }

  @NotNull
  public static List<String> notNullizeContentTitles(@NotNull List<String> mergeContentTitles) {
    String left = StringUtil.notNullize(ThreeSide.LEFT.select(mergeContentTitles), "Your Version");
    String base = StringUtil.notNullize(ThreeSide.BASE.select(mergeContentTitles), "Base Version");
    String right = StringUtil.notNullize(ThreeSide.RIGHT.select(mergeContentTitles), "Server Version");
    return ContainerUtil.list(left, base, right);
  }

  public static class ProxyDiffContext extends DiffContext {
    @NotNull private final MergeContext myMergeContext;

    public ProxyDiffContext(@NotNull MergeContext mergeContext) {
      myMergeContext = mergeContext;
    }

    @Nullable
    @Override
    public Project getProject() {
      return myMergeContext.getProject();
    }

    @Override
    public boolean isWindowFocused() {
      return true;
    }

    @Override
    public boolean isFocused() {
      return myMergeContext.isFocused();
    }

    @Override
    public void requestFocus() {
      myMergeContext.requestFocus();
    }

    @Nullable
    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      return myMergeContext.getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      myMergeContext.putUserData(key, value);
    }
  }
}
