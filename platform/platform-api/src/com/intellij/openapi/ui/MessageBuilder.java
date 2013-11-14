/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

public final class MessageBuilder {
  private final String myMessage;
  private final String myTitle;

  private String myYesText;
  private String myNoText;

  private Project myProject;
  private Icon myIcon;
  private DialogWrapper.DoNotAskOption myDoNotAskOption;

  private MessageBuilder(@NotNull String title, @NotNull String message) {
    myTitle = title;
    myMessage = message;
  }

  public static MessageBuilder yesNo(@NotNull String title, @NotNull String message) {
    return new MessageBuilder(title, message).icon(Messages.getQuestionIcon());
  }

  public MessageBuilder project(@Nullable Project project) {
    myProject = project;
    return this;
  }

  /**
   * @see {@link com.intellij.openapi.ui.Messages#getInformationIcon()}
   * @see {@link com.intellij.openapi.ui.Messages#getWarningIcon()}
   * @see {@link com.intellij.openapi.ui.Messages#getErrorIcon()}
   * @see {@link com.intellij.openapi.ui.Messages#getQuestionIcon()}
   */
  public MessageBuilder icon(@Nullable Icon icon) {
    myIcon = icon;
    return this;
  }

  public MessageBuilder doNotAsk(@NotNull DialogWrapper.DoNotAskOption doNotAskOption) {
    myDoNotAskOption = doNotAskOption;
    return this;
  }

  public MessageBuilder yesText(@NotNull String yesText) {
    myYesText = yesText;
    return this;
  }

  public MessageBuilder noText(@NotNull String noText) {
    myNoText = noText;
    return this;
  }

  @Messages.YesNoResult
  public int show() {
    String yesText = ObjectUtils.chooseNotNull(myYesText, Messages.YES_BUTTON);
    String noText = ObjectUtils.chooseNotNull(myNoText, Messages.NO_BUTTON);
    if (Messages.canShowMacSheetPanel()) {
      return MacMessages.getInstance().showYesNoDialog(myTitle, myMessage, yesText, noText, WindowManager.getInstance().suggestParentWindow(myProject), myDoNotAskOption);
    }
    else {
      //noinspection MagicConstant
      return Messages.showDialog(myProject, myMessage, myTitle, new String[]{yesText, noText}, 0, myIcon, myDoNotAskOption);
    }
  }
}
