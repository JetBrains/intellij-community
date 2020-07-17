/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.options;

import com.intellij.openapi.util.NlsContexts;

/**
 * Thrown to indicate that a configurable component cannot {@link UnnamedConfigurable#apply() apply} entered values.
 */
public class ConfigurationException extends Exception {
  private @NlsContexts.DialogTitle String myTitle = getDefaultTitle();
  private Runnable myQuickFix;
  private Configurable myOriginator;

  /**
   * @param message the detail message describing the problem
   */
  public ConfigurationException(@NlsContexts.DialogMessage String message) {
    super(message);
  }

  /**
   * @param message the detailed message describing the problem
   * @param title   the title describing the problem in short
   */
  public ConfigurationException(@NlsContexts.DialogMessage String message,
                                @NlsContexts.DialogTitle String title) {
    super(message);
    myTitle = title;
  }

  public ConfigurationException(@NlsContexts.DialogMessage String message,
                                Throwable cause,
                                @NlsContexts.DialogTitle String title) {
    super(message, cause);
    myTitle = title;
  }

  @Override
  public @NlsContexts.DialogMessage String getMessage() {
    //noinspection HardCodedStringLiteral
    return super.getMessage();
  }

  /**
   * @return the title describing the problem in short
   */
  public @NlsContexts.DialogTitle String getTitle() {
    return myTitle;
  }

  /**
   * @param quickFix a runnable task that can fix the problem somehow
   */
  public void setQuickFix(Runnable quickFix) {
    myQuickFix = quickFix;
  }

  /**
   * @return a runnable task that can fix the problem somehow, or {@code null} if it is not set
   */
  public Runnable getQuickFix() {
    return myQuickFix;
  }

  public Configurable getOriginator() {
    return myOriginator;
  }

  public void setOriginator(Configurable originator) {
    myOriginator = originator;
  }

  /**
   * @return whether this error should be shown when index isn't complete. Override and return false for errors that
   * might be caused by inability to find some PSI due to index absence.
   */
  public boolean shouldShowInDumbMode() {
    return true;
  }

  public static String getDefaultTitle() {
    return OptionsBundle.message("cannot.save.settings.default.dialog.title");
  }
}