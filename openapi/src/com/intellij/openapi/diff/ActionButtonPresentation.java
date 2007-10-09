/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.diff;

import com.intellij.CommonBundle;

public class ActionButtonPresentation {
  private final boolean myIsEnabled;
  private final boolean myCloseDialog;
  private final String myName;

  public static ActionButtonPresentation createApplyButton(){
    return new ActionButtonPresentation(true, CommonBundle.getApplyButtonText(), true);
  }

  public ActionButtonPresentation(final boolean isEnabled, final String name, final boolean closeDialog) {
    myIsEnabled = isEnabled;
    myName = name;
    myCloseDialog = closeDialog;
  }

  public boolean isEnabled() {
    return myIsEnabled;
  }

  public String getName() {
    return myName;
  }

  public boolean closeDialog() {
    return myCloseDialog;
  }

  public void run(DiffViewer diffViewer){

  }
}
