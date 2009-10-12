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
package com.intellij.application.options;

import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

public class ShareSchemeDialog extends DialogWrapper {
  private final ShareSchemePanel myShareSchemePanel;

  public ShareSchemeDialog() {
    super(true);
    myShareSchemePanel = new ShareSchemePanel();
    setTitle("Share Scheme");
    init();
  }

  public void init(Scheme scheme){
    myShareSchemePanel.setName(scheme.getName());
  }

  protected JComponent createCenterPanel() {
    return myShareSchemePanel.getPanel();
  }

  public String getName(){
    return myShareSchemePanel.getName();
  }

  public String getDescription(){
    return myShareSchemePanel.getDescription();
  }

}
