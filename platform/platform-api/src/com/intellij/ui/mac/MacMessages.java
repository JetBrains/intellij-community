/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ui.mac;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author pegov
 */
public abstract class MacMessages {
  public abstract int showYesNoCancelDialog(String title,
                                            String message,
                                            String defaultButton,
                                            String alternateButton,
                                            String otherButton,
                                            @Nullable Window window,
                                            @Nullable DialogWrapper.DoNotAskOption doNotAskOption);

  public static MacMessages getInstance() {
    return ServiceManager.getService(MacMessages.class);
  }

  /**
   * Buttons are placed starting near the right side of the alert and going toward the left side 
   * (for languages that read left to right). The first three buttons are identified positionally as 
   * NSAlertFirstButtonReturn, NSAlertSecondButtonReturn, NSAlertThirdButtonReturn in the return-code parameter evaluated by the modal 
   * delegate. Subsequent buttons are identified as NSAlertThirdButtonReturn +n, where n is an integer
   * 
   * By default, the first button has a key equivalent of Return, 
   * any button with a title of "Cancel" has a key equivalent of Escape,
   * and any button with the title "Don't Save" has a key equivalent of Command-D (but only if it is not the first button).
   * 
   * http://developer.apple.com/library/mac/#documentation/Cocoa/Reference/ApplicationKit/Classes/NSAlert_Class/Reference/Reference.html
   * 
   * Please, note that Cancel is supposed to be the last button!
   */
  public abstract int showMessageDialog(String title, String message, String[] buttons, boolean errorStyle,
                                @Nullable Window window, int defaultOptionIndex, int focusedOptionIndex, 
                                @Nullable DialogWrapper.DoNotAskOption doNotAskDialogOption);

  public abstract void showOkMessageDialog(String title, String message, String okText, @Nullable Window window);

  public abstract void showOkMessageDialog(String title, String message, String okText);

  public abstract int showYesNoDialog(String title, String message, String yesButton, String noButton, @Nullable Window window);

  public abstract int showYesNoDialog(String title, String message, String yesButton, String noButton, @Nullable Window window,
                                      @Nullable DialogWrapper.DoNotAskOption doNotAskDialogOption);

  public abstract void showErrorDialog(String title, String message, String okButton, @Nullable Window window);
}