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
                                            Window window,
                                            @Nullable DialogWrapper.DoNotAskOption doNotAskOption);

  public static MacMessages getInstance() {
    return ServiceManager.getService(MacMessages.class);
  }
  
  public abstract void showOkMessageDialog(String title, String message, String okText, @Nullable Window window);

  public abstract void showOkMessageDialog(String title, String message, String okText);

  public abstract int showYesNoDialog(String title, String message, String yesButton, String noButton, @Nullable Window window);

  public abstract int showYesNoDialog(String title, String message, String yesButton, String noButton, @Nullable Window window,
                                      @Nullable DialogWrapper.DoNotAskOption doNotAskDialogOption);

  public abstract void showErrorDialog(String title, String message, String okButton, @Nullable Window window);
}