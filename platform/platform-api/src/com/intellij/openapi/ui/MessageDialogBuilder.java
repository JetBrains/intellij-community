// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.mac.MacMessages;
import com.intellij.util.ObjectUtils;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class MessageDialogBuilder<T extends MessageDialogBuilder> {
  protected final String myMessage;
  protected final String myTitle;

  protected String myYesText;
  protected String myNoText;
  protected Project myProject;
  protected Icon myIcon;
  protected DialogWrapper.DoNotAskOption myDoNotAskOption;

  private MessageDialogBuilder(@NotNull @NlsContexts.DialogTitle String title,
                               @NotNull @NlsContexts.DialogMessage String message) {
    myTitle = title;
    myMessage = message;
  }

  @NotNull
  public static YesNo yesNo(@NotNull @NlsContexts.DialogTitle String title, @NotNull @NlsContexts.DialogMessage String message) {
    return new YesNo(title, message).icon(Messages.getQuestionIcon());
  }

  public static YesNoCancel yesNoCancel(@NotNull @NlsContexts.DialogTitle String title,
                                        @NotNull @NlsContexts.DialogMessage String message) {
    return new YesNoCancel(title, message).icon(Messages.getQuestionIcon());
  }

  protected abstract T getThis();

  @NotNull
  public T project(@Nullable Project project) {
    myProject = project;
    return getThis();
  }

  /**
   * @see Messages#getInformationIcon()
   * @see Messages#getWarningIcon()
   * @see Messages#getErrorIcon()
   * @see Messages#getQuestionIcon()
   */
  public T icon(@Nullable Icon icon) {
    myIcon = icon;
    return getThis();
  }

  @NotNull
  public T doNotAsk(@NotNull DialogWrapper.DoNotAskOption doNotAskOption) {
    myDoNotAskOption = doNotAskOption;
    return getThis();
  }

  public T yesText(@NotNull @NlsContexts.Button String yesText) {
    myYesText = yesText;
    return getThis();
  }

  public T noText(@NotNull @NlsContexts.Button String noText) {
    myNoText = noText;
    return getThis();
  }

  public static final class YesNo extends MessageDialogBuilder<YesNo> {
    private YesNo(@NotNull String title, @NotNull String message) {
      super(title, message);
    }

    @Override
    protected YesNo getThis() {
      return this;
    }

    @Messages.YesNoResult
    public int show() {
      String yesText = ObjectUtils.chooseNotNull(myYesText, Messages.getYesButton());
      String noText = ObjectUtils.chooseNotNull(myNoText, Messages.getNoButton());
      try {
        if (Messages.canShowMacSheetPanel() && !Messages.isApplicationInUnitTestOrHeadless()) {
          Window window = WindowManager.getInstance().suggestParentWindow(myProject);
          return MacMessages.getInstance().showYesNoDialog(myTitle, myMessage, yesText, noText, window, myDoNotAskOption);
        }
      }
      catch (Exception ignored) { }

      String[] options = {yesText, noText};
      return Messages.showDialog(myProject, myMessage, myTitle, options, 0, myIcon, myDoNotAskOption) == 0 ? Messages.YES : Messages.NO;
    }

    public boolean isYes() {
      return show() == Messages.YES;
    }
  }

  public static final class YesNoCancel extends MessageDialogBuilder<YesNoCancel> {
    private String myCancelText;

    private YesNoCancel(@NotNull String title, @NotNull String message) {
      super(title, message);
    }

    public YesNoCancel cancelText(@NotNull @NlsContexts.Button String cancelText) {
      myCancelText = cancelText;
      return getThis();
    }

    @Override
    protected YesNoCancel getThis() {
      return this;
    }

    @Messages.YesNoCancelResult
    public int show() {
      String yesText = ObjectUtils.chooseNotNull(myYesText, Messages.getYesButton());
      String noText = ObjectUtils.chooseNotNull(myNoText, Messages.getNoButton());
      String cancelText = ObjectUtils.chooseNotNull(myCancelText, Messages.getCancelButton());
      try {
        if (Messages.canShowMacSheetPanel() && !Messages.isApplicationInUnitTestOrHeadless()) {
          Window window = WindowManager.getInstance().suggestParentWindow(myProject);
          return MacMessages.getInstance().showYesNoCancelDialog(myTitle, myMessage, yesText, noText, cancelText, window, myDoNotAskOption);
        }
      }
      catch (Exception ignored) {}

      String[] options = {yesText, noText, cancelText};
      int buttonNumber = Messages.showDialog(myProject, myMessage, myTitle, options, 0, myIcon, myDoNotAskOption);
      return buttonNumber == 0 ? Messages.YES : buttonNumber == 1 ? Messages.NO : Messages.CANCEL;
    }
  }
}