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

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;

/**
* @author cdr
*/
class IntentionActionWithTextCaching implements Comparable<IntentionActionWithTextCaching> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching");
  private final List<IntentionAction> myOptionIntentions;
  private final List<IntentionAction> myOptionErrorFixes;
  private final String myText;
  private final IntentionAction myAction;
  private final String myDisplayName;
  private final List<IntentionAction> myOptionInspectionFixes;
  private final Icon myIcon;

  IntentionActionWithTextCaching(IntentionAction action){
    this(action, action.getText(), null);
  }

  IntentionActionWithTextCaching(HighlightInfo.IntentionActionDescriptor action){
    this(action.getAction(), action.getDisplayName(), action.getIcon());
  }

  private IntentionActionWithTextCaching(IntentionAction action, String displayName, Icon icon) {
    myIcon = icon;
    myOptionIntentions = new ArrayList<IntentionAction>();
    myOptionErrorFixes = new ArrayList<IntentionAction>();
    myOptionInspectionFixes = new ArrayList<IntentionAction>();
    myText = action.getText();
    // needed for checking errors in user written actions
    //noinspection ConstantConditions
    LOG.assertTrue(myText != null, "action "+action.getClass()+" text returned null");
    myAction = action;
    myDisplayName = displayName;
  }

  String getText() {
    return myText;
  }

  public void addIntention(final IntentionAction action) {
      myOptionIntentions.add(action);
  }
  public void addErrorFix(final IntentionAction action) {
    myOptionErrorFixes.add(action);
  }
  public void addInspectionFix(final IntentionAction action) {
    myOptionInspectionFixes.add(action);
  }

  public IntentionAction getAction() {
    return myAction;
  }

  public List<IntentionAction> getOptionIntentions() {
    return myOptionIntentions;
  }

  public List<IntentionAction> getOptionErrorFixes() {
    return myOptionErrorFixes;
  }

  public List<IntentionAction> getOptionInspectionFixes() {
    return myOptionInspectionFixes;
  }

  public String getToolName() {
    return myDisplayName;
  }

  public String toString() {
    return getText();
  }

  public int compareTo(final IntentionActionWithTextCaching other) {
    if (myAction instanceof Comparable) {
      return ((Comparable)myAction).compareTo(other.getAction());
    }
    else if (other.getAction() instanceof Comparable) {
      return ((Comparable)other.getAction()).compareTo(myAction);
    }
    return Comparing.compare(getText(), other.getText());
  }

  public Icon getIcon() {
    return myIcon;
  }
}
