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

public class ConfigurationException extends Exception {
  public static final String DEFAULT_TITLE = OptionsBundle.message("cannot.save.settings.default.dialog.title");
  private String myTitle = DEFAULT_TITLE;
  private Runnable myQuickFix;

  public ConfigurationException(String message) {
    super(message);
  }

  public ConfigurationException(String message, String title) {
    super(message);
    myTitle = title;
  }

  public String getTitle() {
    return myTitle;
  }

  public void setQuickFix(Runnable quickFix) {
    myQuickFix = quickFix;
  }

  public Runnable getQuickFix() {
    return myQuickFix;
  }

}