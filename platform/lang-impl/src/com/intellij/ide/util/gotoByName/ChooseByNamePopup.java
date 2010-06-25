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

package com.intellij.ide.util.gotoByName;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChooseByNamePopup extends ChooseByNameBase implements ChooseByNamePopupComponent{
  private static final Key<ChooseByNamePopup> CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY = new Key<ChooseByNamePopup>("ChooseByNamePopup");
  private Component myOldFocusOwner = null;

  private ChooseByNamePopup(final Project project, final ChooseByNameModel model, final ChooseByNamePopup oldPopup,
                            final PsiElement context, @Nullable final String predefinedText) {
    super(project, model, oldPopup != null ? oldPopup.getEnteredText() : predefinedText, context);
    if (oldPopup == null && predefinedText != null) {
      setPreselectInitialText(true);
    }
    if (oldPopup != null) { //inherit old focus owner
      myOldFocusOwner = oldPopup.myPreviouslyFocusedComponent;
    }
  }

  public String getEnteredText() {
    return myTextField.getText();
  }

  protected void initUI(final Callback callback, final ModalityState modalityState, boolean allowMultipleSelection) {
    super.initUI(callback, modalityState, allowMultipleSelection);
    //LaterInvocator.enterModal(myTextFieldPanel);
    if (myInitialText != null) {
      rebuildList(0, 0, null, ModalityState.current());
    }
    if (myOldFocusOwner != null){
      myPreviouslyFocusedComponent = myOldFocusOwner;
      myOldFocusOwner = null;
    }
  }

  protected boolean isCheckboxVisible() {
    return true;
  }

  protected boolean isShowListForEmptyPattern(){
    return false;
  }

  protected boolean isCloseByFocusLost(){
    return true;
  }

  protected void showList() {
    final JLayeredPane layeredPane = myTextField.getRootPane().getLayeredPane();
    final Rectangle bounds = myTextFieldPanel.getBounds();
    bounds.y += myTextFieldPanel.getHeight();
    final Dimension preferredScrollPaneSize = myListScrollPane.getPreferredSize();
    preferredScrollPaneSize.width = Math.max(myTextFieldPanel.getWidth(), preferredScrollPaneSize.width);
    if (bounds.y + preferredScrollPaneSize.height > layeredPane.getHeight()){ // clip scroll pane
      preferredScrollPaneSize.height = layeredPane.getHeight() - bounds.y;
    }

    if (preferredScrollPaneSize.width > layeredPane.getWidth() - bounds.x) {
      bounds.x = layeredPane.getX() + Math.max(1, layeredPane.getWidth() - preferredScrollPaneSize.width);
      if (preferredScrollPaneSize.width > layeredPane.getWidth() - bounds.x) {
        preferredScrollPaneSize.width = layeredPane.getWidth() - bounds.x;
        final JScrollBar horizontalScrollBar = myListScrollPane.getHorizontalScrollBar();
        if (horizontalScrollBar != null){
          preferredScrollPaneSize.height += horizontalScrollBar.getPreferredSize().getHeight();
        }
      }
    }

    Rectangle prefferedBounds = new Rectangle(bounds.x, bounds.y, preferredScrollPaneSize.width, preferredScrollPaneSize.height);

    if (myListScrollPane.isVisible()) {
      myListScrollPane.setBounds(prefferedBounds);
    }

    layeredPane.add(myListScrollPane, Integer.valueOf(600));
    layeredPane.moveToFront(myListScrollPane);
    myListScrollPane.validate();
    myListScrollPane.setVisible(true);
  }

  protected void hideList() {
    if (myListScrollPane.isVisible()){
      myListScrollPane.setVisible(false);
    }
  }

  protected void close(final boolean isOk) {
    if (myDisposedFlag){
      return;
    }

    if (isOk){
      myModel.saveInitialCheckBoxState(myCheckBox.isSelected());

      final List<Object> chosenElements = getChosenElements();
      if (chosenElements != null) {
        for (Object element : chosenElements) {
          myActionListener.elementChosen(element);
          String text = myModel.getFullName(element);
          if (text != null) {
            StatisticsManager.getInstance().incUseCount(new StatisticsInfo(statisticsContext(), text));
          }
        }
      } else {
        return;
      }

      if (!chosenElements.isEmpty()){
        final String enteredText = getEnteredText();
        if (enteredText.indexOf('*') >= 0) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.wildcards");
        }
        else {
          for (Object element : chosenElements) {
            final String name = myModel.getElementName(element);
            if (name != null) {
              if (!StringUtil.startsWithIgnoreCase(name, enteredText)) {
                FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.camelprefix");
                break;
              }
            }
          }
        }
      }
      else{
        return;
      }
    }

    myDisposedFlag = true;
    myAlarm.cancelAllRequests();
    myProject.putUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, null);

    //LaterInvocator.leaveModal(myTextFieldPanel);

    cleanupUI();
    myActionListener.onClose ();
  }

  private void cleanupUI() {
    JLayeredPane layeredPane = null;
    try {
      // check if the currently focused component was changed already, so we could leave focus intact
      final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      if (owner != null && SwingUtilities.isDescendingFrom(owner, myTextField)) {
        // Return focus back to the previous focused component if we need to do it and
        // previous focused component is showing.
        if (
          myPreviouslyFocusedComponent instanceof JComponent &&
          myPreviouslyFocusedComponent.isShowing()
        ){
          final JComponent _component = (JComponent)myPreviouslyFocusedComponent;
          LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(_component);
        }
        if (myPreviouslyFocusedComponent != null) {
          myPreviouslyFocusedComponent.requestFocus();
        }
      }

      final JRootPane rootPane = myTextFieldPanel.getRootPane();
      if (rootPane != null) {
        layeredPane = rootPane.getLayeredPane();
        layeredPane.remove(myListScrollPane);
        layeredPane.remove(myTextFieldPanel);
      }
    }
    finally {
      LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(null);
    }

    if (layeredPane != null) {
      layeredPane.validate();
      layeredPane.repaint();
    }
  }

  public static ChooseByNamePopup createPopup(final Project project, final ChooseByNameModel model, final PsiElement context) {
    return createPopup(project, model, context, null);
  }
  public static ChooseByNamePopup createPopup(final Project project, final ChooseByNameModel model, final PsiElement context,
                                              @Nullable final String predefinedText) {
    final ChooseByNamePopup oldPopup = project.getUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY);
    if (oldPopup != null) {
      oldPopup.close(false);
    }
    ChooseByNamePopup newPopup = new ChooseByNamePopup(project, model, oldPopup, context, predefinedText);

    project.putUserData(CHOOSE_BY_NAME_POPUP_IN_PROJECT_KEY, newPopup);
    return newPopup;
  }

  private static final Pattern patternToDetectLinesAndColumns = Pattern.compile("(.*?)(?:\\:|@|,|#)(\\d+)?(?:(?:\\D)(\\d+)?)?");

  public String getNamePattern(String pattern) {
    if (pattern.indexOf(':') != -1 ||
        pattern.indexOf(',') != -1 ||
        pattern.indexOf(';') != -1 ||
        pattern.indexOf('#') != -1 ||
        pattern.indexOf('@') != -1) { // quick test if reg exp should be used
      final Matcher matcher = patternToDetectLinesAndColumns.matcher(pattern);
      if (matcher.matches()) {
        pattern = matcher.group(1);
      }
    }

    return super.getNamePattern(pattern);
  }

  public int getLinePosition() {
    return getLineOrColumn(true);
  }

  private int getLineOrColumn(final boolean line) {
    final Matcher matcher = patternToDetectLinesAndColumns.matcher(getEnteredText());
    if (matcher.matches()) {
      final int groupNumber = line ? 2:3;
      try {
        if(groupNumber <= matcher.groupCount()) {
          final String group = matcher.group(groupNumber);
          if (group != null) return Integer.parseInt(group) - 1;
        }
        if (!line && getLineOrColumn(true) != -1) return 0;
      }
      catch (NumberFormatException ignored) {}
    }

    return -1;
  }

  public int getColumnPosition() {
    return getLineOrColumn(false);
  }
}
