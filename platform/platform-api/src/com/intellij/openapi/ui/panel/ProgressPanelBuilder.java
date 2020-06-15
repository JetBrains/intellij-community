// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.panel;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ProgressPanelBuilder implements GridBagPanelBuilder, PanelBuilder {
  private static final Color SEPARATOR_COLOR = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground();

  private final JProgressBar myProgressBar;
  private String initialLabelText;
  private boolean labelAbove = true;

  private Runnable cancelAction;
  private Runnable resumeAction;
  private Runnable pauseAction;

  private String  cancelText = "Cancel";
  private boolean cancelAsButton;
  private boolean smallVariant;

  private boolean commentEnabled = true;
  private boolean text2Enabled;
  private boolean topSeparatorEnabled;

  public ProgressPanelBuilder(JProgressBar progressBar) {
    myProgressBar = progressBar;
  }

  /**
     * Set label text.
     *
     * @param text label text
     * @return <code>this</code>
     */
  public ProgressPanelBuilder withLabel(@NotNull String text) {
    initialLabelText = text;
    return this;
  }

  /**
     * Move comment to the left of the progress bar. Default position is above the progress bar.
     *
     * @return <code>this</code>
     */
  public ProgressPanelBuilder moveLabelLeft() {
    labelAbove = false;
    return this;
  }

  /**
     * Enables cancel button and sets action for it. Can't coexist with play and pause actions.
     *
     * @param cancelAction <code>Runnable</code> action.
     * @return <code>this</code>
     */
  public ProgressPanelBuilder withCancel(@NotNull Runnable cancelAction) {
    this.cancelAction = cancelAction;
    return this;
  }

  /**
     * If cancel button looks like a button (see {@link #andCancelAsButton()}) sets the text to be displayed on cancel button.
     * Otherwise sets the text to be displayed under the progressbar on mouse hover over the cancel icon.
     *
     * "Cancel" is the default text.
     *
     * @return <code>this</code>
     */
  public ProgressPanelBuilder andCancelText(String cancelText) {
    this.cancelText = cancelText;
    return this;
  }

  /**
     * Cancel button will look like an ordinary button rather than as icon. Default is icon styled cancel button.
     *
     * @return <code>this</code>
     */
  public ProgressPanelBuilder andCancelAsButton() {
    this.cancelAsButton = true;
    return this;
  }

  /**
     * Enables play button (icon styled) and sets action for it. Can't coexist with cancel action.
     *
     * @param playAction <code>Runnable</code> action.
     * @return <code>this</code>
     */
  public ProgressPanelBuilder withResume(@NotNull Runnable playAction) {
    this.resumeAction = playAction;
    return this;
  }

  /**
     * Enables pause button (icon styled) and sets action for it. Can't coexist with cancel action.
     *
     * @param pauseAction <code>Runnable</code> action.
     * @return <code>this</code>
     */
  public ProgressPanelBuilder withPause(@NotNull Runnable pauseAction) {
    this.pauseAction = pauseAction;
    return this;
  }


  /**
     * Switch to small icons version.
     *
     * @return <code>this</code>
     */
  public ProgressPanelBuilder andSmallIcons() {
    this.smallVariant = true;
    return this;
  }

  /**
     * Switch off the comment and don't reserve place for it in the layout. This makes overall panel height smaller.
     *
     * @return <code>this</code>
     */
  public ProgressPanelBuilder withoutComment() {
    this.commentEnabled = false;
    return this;
  }

  @NotNull
  public ProgressPanelBuilder withText2() {
    text2Enabled = true;
    return this;
  }

  /**
     * Enable separator on top of the panel.
     *
     * @return <code>this</code>
     */
  public ProgressPanelBuilder withTopSeparator() {
    this.topSeparatorEnabled = true;
    return this;
  }

  @Override
  @NotNull
  public JPanel createPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
                                                   null, 0, 0);
    addToPanel(panel, gc, false);
    return panel;
  }

  @Override
  public boolean constrainsValid() {
    return true;
  }

  @Override
  public void addToPanel(JPanel panel, GridBagConstraints gc, boolean splitColumns) {
    if (constrainsValid()) {
      new LabeledPanelImpl().addToPanel(panel, gc);
    }
  }

  @Override
  public int gridWidth() {
    int width = labelAbove ? 1 : 2;
    width += (cancelAction != null) ? 1 : 0;
    width += (resumeAction != null && pauseAction != null) ? 1 : 0;
    return width;
  }

  private class LabeledPanelImpl extends ProgressPanel {
    private final JLabel label;
    private final JLabel comment;
    private final JLabel text2;

    private String myCommentText = emptyComment();
    private boolean myServiceComment = false;

    private final IconButton cancelIcon;
    private final IconButton resumeIcon;
    private final IconButton pauseIcon;

    private JButton myCancelButtonAsButton;
    private InplaceButton myCancelButton;
    private InplaceButton mySuspendButton;

    private final SeparatorComponent mySeparatorComponent = new SeparatorComponent(SEPARATOR_COLOR, SeparatorOrientation.HORIZONTAL);

    private State state = State.PLAYING;

    private LabeledPanelImpl() {
      label = new JLabel(StringUtil.isNotEmpty(initialLabelText) ? initialLabelText : " ");

      comment = new JLabel(myCommentText);
      comment.setForeground(UIUtil.getContextHelpForeground());

      if (text2Enabled) {
        text2 = new JLabel();
        text2.setForeground(UIUtil.getContextHelpForeground());
      }
      else {
        text2 = null;
      }

      if (SystemInfo.isMac) {
        Font font = comment.getFont();
        float size = font.getSize2D();
        Font smallFont = font.deriveFont(size - 2.0f);
        comment.setFont(smallFont);
        if (text2 != null) {
          text2.setFont(smallFont);
        }
      }

      if (StringUtil.isNotEmpty(initialLabelText)) {
        Dimension size = comment.getPreferredSize();
        size.width = label.getMinimumSize().width;
        comment.setMinimumSize(size);
        if (text2 != null) {
          text2.setMinimumSize(size);
        }
      }

      cancelIcon = new IconButton(null,
                                  smallVariant ? AllIcons.Process.StopSmall : AllIcons.Process.Stop,
                                  smallVariant ? AllIcons.Process.StopSmallHovered : AllIcons.Process.StopHovered);
      resumeIcon = new IconButton(null,
                                  smallVariant ? AllIcons.Process.ProgressResumeSmall : AllIcons.Process.ProgressResume,
                                  smallVariant ? AllIcons.Process.ProgressResumeSmallHover : AllIcons.Process.ProgressResumeHover);
      pauseIcon = new IconButton(null,
                                 smallVariant ? AllIcons.Process.ProgressPauseSmall : AllIcons.Process.ProgressPause,
                                 smallVariant ? AllIcons.Process.ProgressPauseSmallHover : AllIcons.Process.ProgressPauseHover);
    }

    private String emptyComment() {
      return commentEnabled ? " " : "";
    }

    @Override
    public String getLabelText() {
      return label.getText();
    }

    @Override
    public void setLabelText(String labelText) {
      label.setText(StringUtil.isNotEmpty(labelText) ? labelText : " ");

      if (StringUtil.isNotEmpty(labelText)) {
        Dimension size = comment.getPreferredSize();
        size.width = label.getMinimumSize().width;
        comment.setMinimumSize(size);
        if (text2 != null) {
          text2.setMinimumSize(size);
        }
      }
    }

    @Override
    public String getCommentText() {
      return myServiceComment ? myCommentText : comment.getText();
    }

    @Override
    public void setCommentText(String commentText) {
      if (commentEnabled) {
        setCommentText(commentText, false);
      }
    }

    @Override
    public void setLabelEnabled(boolean enabled) {
      label.setEnabled(enabled);
    }

    @Override
    public void setCommentEnabled(boolean enabled) {
      comment.setEnabled(enabled);
    }

    @Override
    public void setText2(@Nullable String text) {
      if (text2 != null) {
        text2.setText(text);
        text2.setVisible(true);
      }
    }

    @Override
    public void setText2Enabled(boolean enabled) {
      if (text2 != null) {
        text2.setEnabled(enabled);
      }
    }

    @Override
    public void setSeparatorEnabled(boolean enabled) {
      mySeparatorComponent.setVisible(enabled);
    }

    @Override
    public @Nullable JButton getCancelButtonAsButton() {
      return myCancelButtonAsButton;
    }

    @Nullable
    @Override
    public InplaceButton getCancelButton() {
      return myCancelButton;
    }

    @Nullable
    @Override
    public InplaceButton getSuspendButton() {
      return mySuspendButton;
    }

    private void setCommentText(String commentText, boolean serviceComment) {
      if (serviceComment) {
        myServiceComment = commentText != null;
        comment.setText(commentText == null ? myCommentText : commentText);
      }
      else {
        myCommentText = StringUtil.isNotEmpty(commentText) ? commentText : emptyComment();
        if (!myServiceComment) {
          comment.setText(myCommentText);
        }
      }
    }

    @NotNull
    @Override
    public State getState() {
      return state;
    }

    @Override
    public void setState(@NotNull State state) {
      if (this.state == state || state == State.CANCELLED || mySuspendButton == null) {
        return;
      }

      this.state = state;

      if (state == State.PLAYING) {
        mySuspendButton.setIcons(pauseIcon);
        setCommentText(IdeBundle.message("comment.text.pause"), true);
      }
      else {
        mySuspendButton.setIcons(resumeIcon);
        setCommentText(IdeBundle.message("comment.text.paused"), true);
      }
    }

    private void addToPanel(JPanel panel, GridBagConstraints gc) {
      gc.gridx = 0;
      gc.anchor = GridBagConstraints.LINE_START;
      gc.fill = GridBagConstraints.HORIZONTAL;

      if (topSeparatorEnabled) {
        gc.insets = JBUI.insets(14, 0, 10, 0);
        gc.gridwidth = gridWidth();
        gc.weightx = 1.0;
        panel.add(mySeparatorComponent, gc);
        gc.gridy++;
      }

      gc.weightx = 0.0;
      gc.gridwidth = 1;
      gc.insets = JBUI.insets(topSeparatorEnabled || smallVariant ? 0 : 12, 13, 0, labelAbove ? 13 : 0);
      panel.add(label, gc);

      if (labelAbove) {
        gc.insets = JBUI.insets(4, 13, 4, 0);
        gc.gridy++;
      }
      else {
        gc.insets = JBUI.insets(topSeparatorEnabled || smallVariant ? 2 : 14, 12, 0, 0);
        gc.gridx++;
      }

      gc.weightx = 1.0;
      panel.add(myProgressBar, gc);
      gc.gridx++;

      myProgressBar.putClientProperty(DECORATED_PANEL_PROPERTY, this);

      gc.weightx = 0.0;
      gc.insets = JBUI.insets(labelAbove || topSeparatorEnabled || smallVariant ? 1 : 14,
                              UIUtil.isUnderWin10LookAndFeel() ? 9 : 10,
                              0, 13);

      if (cancelAction != null) {
        if (cancelAsButton) {
          myCancelButtonAsButton = new JButton(cancelText);
          myCancelButtonAsButton.addActionListener((e) -> cancelAction.run());
          panel.add(myCancelButtonAsButton, gc);
        }
        else {
          myCancelButton = new InplaceButton(cancelIcon, a -> {
            myCancelButton.setPainting(false);
            state = State.CANCELLED;
            cancelAction.run();
          }).setFillBg(false);
        }
      }
      if (resumeAction != null && pauseAction != null) {
        mySuspendButton = new InplaceButton(pauseIcon, a -> {
          if (state == State.CANCELLED) {
            return;
          }
          if (state == State.PLAYING) {
            mySuspendButton.setIcons(resumeIcon);
            state = State.PAUSED;
            setCommentText(IdeBundle.message("comment.text.resume"), true);
            pauseAction.run();
          }
          else {
            mySuspendButton.setIcons(pauseIcon);
            state = State.PLAYING;
            setCommentText(IdeBundle.message("comment.text.pause"), true);
            resumeAction.run();
          }
        }).setFillBg(false);
      }

      if (mySuspendButton != null) {
        addButton(panel, gc, mySuspendButton, false);
      }
      if (myCancelButton != null) {
        if (mySuspendButton != null) {
          gc.gridx++;
        }
        addButton(panel, gc, myCancelButton, true);
      }

      if (commentEnabled) {
        addLabel(panel, gc, comment);
      }

      if (text2 != null) {
        addLabel(panel, gc, text2);
        text2.setVisible(false);
      }

      gc.gridy++;
    }

    private void addLabel(@NotNull JPanel panel, @NotNull GridBagConstraints gc, @NotNull JComponent label) {
      gc.gridy++;
      gc.gridx = labelAbove ? 0 : 1;
      gc.insets = labelAbove ? JBUI.insets(-1, 13, 0, 13) : JBUI.insets(-1, 12, 0, 13);
      gc.weightx = 1.0;
      gc.anchor = GridBagConstraints.LINE_START;
      gc.fill = GridBagConstraints.HORIZONTAL;
      panel.add(label, gc);
    }

    private void addButton(@NotNull JPanel panel, @NotNull GridBagConstraints gc, @NotNull InplaceButton button, boolean cancel) {
      button.setMinimumSize(button.getPreferredSize());

      if (commentEnabled) {
        button.addMouseListener(new HoverListener(cancel));
      }

      gc.anchor = GridBagConstraints.EAST;
      gc.fill = GridBagConstraints.NONE;
      panel.add(button, gc);
    }

    private class HoverListener extends MouseAdapter {
      private final boolean myCancel;

      private HoverListener(boolean cancel) {
        myCancel = cancel;
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        if (myCancel) {
          setCommentText(cancelText, true);
        }
        else {
          setCommentText(state == State.PLAYING ? IdeBundle.message("comment.text.pause") : IdeBundle.message("comment.text.resume"), true);
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setCommentText(state == State.PAUSED ? IdeBundle.message("comment.text.paused") : null, true);
      }
    }
  }
}
