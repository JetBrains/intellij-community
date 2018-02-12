/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.ui.Messages.wrapToScrollPaneIfNeeded;

class MessageDialog extends DialogWrapper {
  protected String myMessage;
  protected String[] myOptions;
  protected int myDefaultOptionIndex;
  protected int myFocusedOptionIndex;
  protected Icon myIcon;
  private MessagesBorderLayout myLayout;

  public MessageDialog(@Nullable Project project,
                       String message,
                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                       @NotNull String[] options,
                       int defaultOptionIndex,
                       @Nullable Icon icon,
                       boolean canBeParent) {
    this(project, message, title, options, defaultOptionIndex, -1, icon, canBeParent);
  }

  public MessageDialog(@Nullable Project project,
                       String message,
                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                       @NotNull String[] options,
                       int defaultOptionIndex,
                       int focusedOptionIndex,
                       @Nullable Icon icon,
                       @Nullable DoNotAskOption doNotAskOption,
                       boolean canBeParent) {
    super(project, canBeParent);
    _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption);
  }

  public MessageDialog(@Nullable Project project,
                       String message,
                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                       @NotNull String[] options,
                       int defaultOptionIndex,
                       int focusedOptionIndex,
                       @Nullable Icon icon,
                       boolean canBeParent) {
    super(project, canBeParent);
    _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, null);
  }

  public MessageDialog(@NotNull Component parent,
                       String message,
                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                       @NotNull String[] options,
                       int defaultOptionIndex,
                       @Nullable Icon icon) {
    this(parent, message, title, options, defaultOptionIndex, icon, false);
  }

  public MessageDialog(@NotNull Component parent,
                       String message,
                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                       @NotNull String[] options,
                       int defaultOptionIndex,
                       @Nullable Icon icon,
                       boolean canBeParent) {
    this(parent, message, title, options, defaultOptionIndex, -1, icon, canBeParent);
  }

  public MessageDialog(@NotNull Component parent,
                       String message,
                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                       @NotNull String[] options,
                       int defaultOptionIndex,
                       int focusedOptionIndex,
                       @Nullable Icon icon,
                       boolean canBeParent) {
    super(parent, canBeParent);
    _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, null);
  }

  public MessageDialog(String message,
                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                       @NotNull String[] options,
                       int defaultOptionIndex,
                       @Nullable Icon icon) {
    this(message, title, options, defaultOptionIndex, icon, false);
  }

  public MessageDialog(String message,
                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                       @NotNull String[] options,
                       int defaultOptionIndex,
                       @Nullable Icon icon,
                       boolean canBeParent) {
    super(canBeParent);
    _init(title, message, options, defaultOptionIndex, -1, icon, null);
  }

  public MessageDialog(String message,
                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                       @NotNull String[] options,
                       int defaultOptionIndex,
                       int focusedOptionIndex,
                       @Nullable Icon icon,
                       @Nullable DoNotAskOption doNotAskOption) {
    super(false);
    _init(title, message, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption);
  }

  public MessageDialog(String message,
                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                       @NotNull String[] options,
                       int defaultOptionIndex,
                       Icon icon,
                       DoNotAskOption doNotAskOption) {
    this(message, title, options, defaultOptionIndex, -1, icon, doNotAskOption);
  }

  protected MessageDialog() {
    super(false);
  }

  protected MessageDialog(Project project) {
    super(project, false);
  }

  public MessageDialog(Project project, boolean canBeParent) {
    super(project, canBeParent);
  }

  protected void _init(@Nls(capitalization = Nls.Capitalization.Title) String title,
                       String message,
                       @NotNull String[] options,
                       int defaultOptionIndex,
                       int focusedOptionIndex,
                       @Nullable Icon icon,
                       @Nullable DoNotAskOption doNotAskOption) {
    setTitle(title);
    if (Messages.isMacSheetEmulation()) {
      setUndecorated(true);
    }
    myMessage = message;
    myOptions = options;
    myDefaultOptionIndex = defaultOptionIndex;
    myFocusedOptionIndex = focusedOptionIndex;
    myIcon = icon;
    if (!SystemInfo.isMac) {
      setButtonsAlignment(SwingConstants.CENTER);
    }
    setDoNotAskOption(doNotAskOption);
    init();
    if (Messages.isMacSheetEmulation()) {
      MacUtil.adjustFocusTraversal(myDisposable);
    }
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    Action[] actions = new Action[myOptions.length];
    for (int i = 0; i < myOptions.length; i++) {
      String option = myOptions[i];
      final int exitCode = i;
      actions[i] = new AbstractAction(UIUtil.replaceMnemonicAmpersand(option)) {
        @Override
        public void actionPerformed(ActionEvent e) {
          close(exitCode, true);
        }
      };

      if (i == myDefaultOptionIndex) {
        actions[i].putValue(DEFAULT_ACTION, Boolean.TRUE);
      }

      if (i == myFocusedOptionIndex) {
        actions[i].putValue(FOCUSED_ACTION, Boolean.TRUE);
      }

      UIUtil.assignMnemonic(option, actions[i]);

    }
    return actions;
  }

  @Override
  public void doCancelAction() {
    close(-1);
  }

  @Override
  protected JComponent createCenterPanel() {
    return doCreateCenterPanel();
  }

  @NotNull
  @Override
  LayoutManager createRootLayout() {
    return Messages.isMacSheetEmulation() ? myLayout = new MessagesBorderLayout() : super.createRootLayout();
  }

  @Override
  protected void dispose() {
    if (Messages.isMacSheetEmulation()) {
      animate();
    }
    else {
      super.dispose();
    }
  }

  @Override
  public void show() {
    if (Messages.isMacSheetEmulation()) {
      setInitialLocationCallback(() -> {
        JRootPane rootPane = SwingUtilities.getRootPane(getWindow().getParent());
        if (rootPane == null) {
          rootPane = SwingUtilities.getRootPane(getWindow().getOwner());
        }

        Point p = rootPane.getLocationOnScreen();
        p.x += (rootPane.getWidth() - getWindow().getWidth()) / 2;
        return p;
      });
      animate();
      try {
        Class.forName("java.awt.Window").getDeclaredMethod("setOpacity", float.class).invoke(getPeer().getWindow(), .8f);
      }
      catch (Exception ignored) {
      }
      setAutoAdjustable(false);
      setSize(getPreferredSize().width, 0);//initial state before animation, zero height
    }
    super.show();
  }

  private void animate() {
    final int height = getPreferredSize().height;
    final int frameCount = 10;
    final boolean toClose = isShowing();


    final AtomicInteger i = new AtomicInteger(-1);
    final Alarm animator = new Alarm(myDisposable);
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        int state = i.addAndGet(1);

        double linearProgress = (double)state / frameCount;
        if (toClose) {
          linearProgress = 1 - linearProgress;
        }
        myLayout.setPhase((1 - Math.cos(Math.PI * linearProgress)) / 2);
        Window window = getPeer().getWindow();
        Rectangle bounds = window.getBounds();
        bounds.height = (int)(height * myLayout.getPhase());

        window.setBounds(bounds);

        if (state == 0 && !toClose && window.getOwner() instanceof IdeFrame) {
          WindowManager.getInstance().requestUserAttention((IdeFrame)window.getOwner(), true);
        }

        if (state < frameCount) {
          animator.addRequest(this, 10);
        }
        else if (toClose) {
          MessageDialog.super.dispose();
        }
      }
    };
    animator.addRequest(runnable, 10, ModalityState.stateForComponent(getRootPane()));
  }

  protected JComponent doCreateCenterPanel() {
    JPanel panel = createIconPanel();
    if (myMessage != null) {
      JTextPane messageComponent = createMessageComponent(myMessage);
      panel.add(wrapToScrollPaneIfNeeded(messageComponent, 100, 10), BorderLayout.CENTER);
    }
    return panel;
  }

  @NotNull
  protected JPanel createIconPanel() {
    JPanel panel = new JPanel(new BorderLayout(15, 0));
    if (myIcon != null) {
      JLabel iconLabel = new JLabel(myIcon);
      Container container = new Container();
      container.setLayout(new BorderLayout());
      container.add(iconLabel, BorderLayout.NORTH);
      panel.add(container, BorderLayout.WEST);
    }
    return panel;
  }

  @NotNull
  protected JPanel createMessagePanel() {
    JPanel messagePanel = new JPanel(new BorderLayout());
    if (myMessage != null) {
      JLabel textLabel = new JLabel(myMessage);
      textLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
      textLabel.setUI(new MultiLineLabelUI());
      messagePanel.add(textLabel, BorderLayout.NORTH);
    }
    return messagePanel;
  }

  protected JTextPane createMessageComponent(final String message) {
    final JTextPane messageComponent = new JTextPane();
    return Messages.configureMessagePaneUi(messageComponent, message);
  }

  @Override
  protected void doHelpAction() {
    // do nothing
  }
}
