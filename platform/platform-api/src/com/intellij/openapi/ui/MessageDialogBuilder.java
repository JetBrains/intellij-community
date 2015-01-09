/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.mac.MacMessages;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class MessageDialogBuilder<T extends MessageDialogBuilder> {
  protected final String myMessage;
  protected final String myTitle;

  protected String myYesText;
  protected String myNoText;

  protected Project myProject;
  protected Icon myIcon;
  protected DialogWrapper.DoNotAskOption myDoNotAskOption;

  private MessageDialogBuilder(@NotNull String title, @NotNull String message) {
    myTitle = title;
    myMessage = message;
  }

  @NotNull
  public static YesNo yesNo(@NotNull String title, @NotNull String message) {
    return new YesNo(title, message).icon(Messages.getQuestionIcon());
  }

  public static YesNoCancel yesNoCancel(@NotNull String title, @NotNull String message) {
    return new YesNoCancel(title, message).icon(Messages.getQuestionIcon());
  }

  protected abstract T getThis();

  @NotNull
  public T project(@Nullable Project project) {
    myProject = project;
    return getThis();
  }

  /**
   * @see {@link com.intellij.openapi.ui.Messages#getInformationIcon()}
   * @see {@link com.intellij.openapi.ui.Messages#getWarningIcon()}
   * @see {@link com.intellij.openapi.ui.Messages#getErrorIcon()}
   * @see {@link com.intellij.openapi.ui.Messages#getQuestionIcon()}
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

  public T yesText(@NotNull String yesText) {
    myYesText = yesText;
    return getThis();
  }

  public T noText(@NotNull String noText) {
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
      String yesText = ObjectUtils.chooseNotNull(myYesText, Messages.YES_BUTTON);
      String noText = ObjectUtils.chooseNotNull(myNoText, Messages.NO_BUTTON);
      try {
        if (Messages.canShowMacSheetPanel() && !Messages.isApplicationInUnitTestOrHeadless()) {
          return MacMessages.getInstance().showYesNoDialog(myTitle, myMessage, yesText, noText, WindowManager.getInstance().suggestParentWindow(myProject), myDoNotAskOption);
        }
      } catch (Exception ignored) {}

      return Messages.showDialog(myProject, myMessage, myTitle, new String[]{yesText, noText}, 0, myIcon, myDoNotAskOption) == 0 ? Messages.YES : Messages.NO;

    }

    public boolean is() {
      return show() == Messages.YES;
    }
  }

  public static final class YesNoCancel extends MessageDialogBuilder<YesNoCancel> {
    private String myCancelText;

    private YesNoCancel(@NotNull String title, @NotNull String message) {
      super(title, message);
    }

    public YesNoCancel cancelText(@NotNull String cancelText) {
      myCancelText = cancelText;
      return getThis();
    }

    @Override
    protected YesNoCancel getThis() {
      return this;
    }

    @Messages.YesNoCancelResult
    public int show() {
      String yesText = ObjectUtils.chooseNotNull(myYesText, Messages.YES_BUTTON);
      String noText = ObjectUtils.chooseNotNull(myNoText, Messages.NO_BUTTON);
      String cancelText = ObjectUtils.chooseNotNull(myCancelText, Messages.CANCEL_BUTTON);
      try {
        if (Messages.canShowMacSheetPanel() && !Messages.isApplicationInUnitTestOrHeadless()) {
          return MacMessages.getInstance().showYesNoCancelDialog(myTitle, myMessage, yesText, noText, cancelText, WindowManager.getInstance().suggestParentWindow(myProject), myDoNotAskOption);
        }
      }
      catch (Exception ignored) {}

      int buttonNumber = Messages.showDialog(myProject, myMessage, myTitle, new String[]{yesText, noText, cancelText}, 0, myIcon, myDoNotAskOption);
      return buttonNumber == 0 ? Messages.YES : buttonNumber == 1 ? Messages.NO : Messages.CANCEL;

    }
  }
}
