/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.usages;

import com.intellij.usageView.UsageViewBundle;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 21, 2004
 * Time: 9:15:39 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageViewPresentation {
  private String myTabText;
  private String myScopeText;
  private String myUsagesString;
  private String myTargetsNodeText = UsageViewBundle.message("node.targets"); // Default value. to be overwritten in most cases.
  private String myNonCodeUsagesString = UsageViewBundle.message("node.non.code.usages");
  private String myCodeUsagesString = UsageViewBundle.message("node.found.usages");
  private boolean myShowReadOnlyStatusAsRed = false;
  private boolean myShowCancelButton = false;
  private boolean myOpenInNewTab = true;
  private boolean myCodeUsages = true;
  private String myUsagesWord = UsageViewBundle.message("usage.name");

  private List<Action> myNotFoundActions;

  public String getTabText() {
    return myTabText;
  }

  public void setTabText(String tabText) {
    myTabText = tabText;
  }

  public String getScopeText() {
    return myScopeText;
  }

  public void setScopeText(String scopeText) {
    myScopeText = scopeText;
  }

  public boolean isShowReadOnlyStatusAsRed() {
    return myShowReadOnlyStatusAsRed;
  }

  public void setShowReadOnlyStatusAsRed(boolean showReadOnlyStatusAsRed) {
    myShowReadOnlyStatusAsRed = showReadOnlyStatusAsRed;
  }

  public String getUsagesString() {
    return myUsagesString;
  }

  public void setUsagesString(String usagesString) {
    myUsagesString = usagesString;
  }

  @Nullable("null means the targets node must not be visible")
  public String getTargetsNodeText() {
    return myTargetsNodeText;
  }

  public void setTargetsNodeText(String targetsNodeText) {
    myTargetsNodeText = targetsNodeText;
  }

  public boolean isShowCancelButton() {
    return myShowCancelButton;
  }

  public void setShowCancelButton(boolean showCancelButton) {
    myShowCancelButton = showCancelButton;
  }

  public String getNonCodeUsagesString() {
    return myNonCodeUsagesString;
  }

  public void setNonCodeUsagesString(String nonCodeUsagesString) {
    myNonCodeUsagesString = nonCodeUsagesString;
  }

  public String getCodeUsagesString() {
    return myCodeUsagesString;
  }

  public void setCodeUsagesString(String codeUsagesString) {
    myCodeUsagesString = codeUsagesString;
  }

  public boolean isOpenInNewTab() {
    return myOpenInNewTab;
  }

  public void setOpenInNewTab(boolean openInNewTab) {
    myOpenInNewTab = openInNewTab;
  }

  public boolean isCodeUsages() {
    return myCodeUsages;
  }

  public void setCodeUsages(final boolean codeUsages) {
    myCodeUsages = codeUsages;
  }

  public void addNotFoundAction(Action _action) {
    if (myNotFoundActions == null) myNotFoundActions = new ArrayList<Action>();
    myNotFoundActions.add(_action);
  }

  public List<Action> getNotFoundActions() {
    return myNotFoundActions;
  }

  public String getUsagesWord() {
    return myUsagesWord;
  }

  public void setUsagesWord(final String usagesWord) {
    myUsagesWord = usagesWord;
  }
}

