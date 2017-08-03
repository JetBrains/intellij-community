/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.openapi.ui.popup;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.List;

/**
 * Factory class for creating popup chooser windows (similar to the Code | Generate... popup) and various notifications/confirmations.
 *
 * @author mike
 * @since 6.0
 */
public abstract class JBPopupFactory {
  /**
   * Returns the popup factory instance.
   *
   * @return the popup factory instance.
   */
  public static JBPopupFactory getInstance() {
    return ServiceManager.getService(JBPopupFactory.class);
  }

  @NotNull
  public PopupChooserBuilder createListPopupBuilder(@NotNull JList list) {
    return new PopupChooserBuilder(list);
  }

  /**
   * Creates a popup with the specified title and two options, Yes and No.
   *
   * @param title the title of the popup.
   * @param onYes the runnable which is executed when the Yes option is selected.
   * @param defaultOptionIndex the index of the option which is selected by default.
   * @return the popup instance.
   */
  @NotNull
  public abstract ListPopup createConfirmation(String title, Runnable onYes, int defaultOptionIndex);

  /**
   * Creates a popup allowing to choose one of two specified options and execute code when one of them is selected.
   *
   * @param title the title of the popup.
   * @param yesText the title for the Yes option.
   * @param noText the title for the No option.
   * @param onYes the runnable which is executed when the Yes option is selected.
   * @param defaultOptionIndex the index of the option which is selected by default.
   * @return the popup instance.
   */
  @NotNull
  public abstract ListPopup createConfirmation(String title, String yesText, String noText, Runnable onYes, int defaultOptionIndex);

  /**
   * Creates a popup allowing to choose one of two specified options and execute code when either of them is selected.
   *
   * @param title the title of the popup.
   * @param yesText the title for the Yes option.
   * @param noText the title for the No option.
   * @param onYes the runnable which is executed when the Yes option is selected.
   * @param onNo the runnable which is executed when the No option is selected.
   * @param defaultOptionIndex the index of the option which is selected by default.
   * @return the popup instance.
   */
  @NotNull
  public abstract ListPopup createConfirmation(String title,
                                               String yesText,
                                               String noText,
                                               Runnable onYes,
                                               Runnable onNo,
                                               int defaultOptionIndex);

  @NotNull
  public abstract ListPopupStep createActionsStep(@NotNull ActionGroup actionGroup,
                                                  @NotNull DataContext dataContext,
                                                  boolean showNumbers,
                                                  boolean showDisabledActions,
                                                  String title,
                                                  Component component,
                                                  boolean honorActionMnemonics);

  @NotNull
  public abstract ListPopupStep createActionsStep(@NotNull ActionGroup actionGroup,
                                                  @NotNull DataContext dataContext,
                                                  boolean showNumbers,
                                                  boolean showDisabledActions,
                                                  String title,
                                                  Component component,
                                                  boolean honorActionMnemonics,
                                                  int defaultOptionIndex, final boolean autoSelectionEnabled);

  @NotNull
  public abstract RelativePoint guessBestPopupLocation(@NotNull JComponent component);

  public boolean isChildPopupFocused(@Nullable Component parent) {
    return getChildFocusedPopup(parent) != null;
  }

  public JBPopup getChildFocusedPopup(@Nullable Component parent) {
    if (parent == null) return null;
    List<JBPopup> popups = getChildPopups(parent);
    for (JBPopup each : popups) {
      if (each.isFocused()) return each;
      JBPopup childFocusedPopup = getChildFocusedPopup(each.getContent());
      if (childFocusedPopup != null) {
        return childFocusedPopup;
      }
    }
    return null;
  }

  /**
   * Possible ways to select actions in a popup from keyboard.
   */
  public enum ActionSelectionAid {
    /**
     * The actions in a popup are prefixed by numbers (indexes in the list).
     */
    NUMBERING,

    /**
     * Same as numbering, but will allow A-Z 'numbers' when out of 0-9 range.
     */
    ALPHA_NUMBERING,

    /**
     * The actions in a popup can be selected by typing part of the action's text.
     */
    SPEEDSEARCH,

    /**
     * The actions in a popup can be selected by pressing the character from the action's text prefixed with
     * an &amp; character.
     */
    MNEMONICS
  }

  /**
   * Creates a popup allowing to choose one of the actions from the specified action group.
   *
   * @param title the title of the popup.
   * @param actionGroup the action group from which the popup is built.
   * @param dataContext the data context which provides the data for the selected action
   * @param selectionAidMethod keyboard selection mode for actions in the popup.
   * @param showDisabledActions if true, disabled actions are shown as disabled; if false, disabled actions are not shown
   * @return the popup instance.
   */
  @NotNull
  public abstract ListPopup createActionGroupPopup(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String title,
                                                   @NotNull ActionGroup actionGroup,
                                                   @NotNull DataContext dataContext,
                                                   ActionSelectionAid selectionAidMethod,
                                                   boolean showDisabledActions);

  /**
   * Creates a popup allowing to choose one of the actions from the specified action group.
   *
   * @param title the title of the popup.
   * @param actionGroup the action group from which the popup is built.
   * @param dataContext the data context which provides the data for the selected action
   * @param selectionAidMethod keyboard selection mode for actions in the popup.
   * @param showDisabledActions if true, disabled actions are shown as disabled; if false, disabled actions are not shown
   * @param actionPlace action place for ActionManager to use when creating the popup
   * @return the popup instance.
   */
  @NotNull
  public abstract ListPopup createActionGroupPopup(@Nls(capitalization = Nls.Capitalization.Title) String title,
                                                   @NotNull ActionGroup actionGroup,
                                                   @NotNull DataContext dataContext,
                                                   ActionSelectionAid selectionAidMethod,
                                                   boolean showDisabledActions,
                                                   @Nullable String actionPlace);

  /**
   * Creates a popup allowing to choose one of the actions from the specified action group.
   *
   * @param title the title of the popup.
   * @param actionGroup the action group from which the popup is built.
   * @param dataContext the data context which provides the data for the selected action
   * @param selectionAidMethod keyboard selection mode for actions in the popup.
   * @param showDisabledActions if true, disabled actions are shown as disabled; if false, disabled actions are not shown
   * @param disposeCallback method which is called when the popup is closed (either by selecting an action or by canceling)
   * @param maxRowCount maximum number of popup rows visible at once (if there are more actions in the action group, a scrollbar
   *                    is displayed)
   * @return the popup instance.
   */
  @NotNull
  public abstract ListPopup createActionGroupPopup(@Nls(capitalization = Nls.Capitalization.Title) String title,
                                                   @NotNull ActionGroup actionGroup,
                                                   @NotNull DataContext dataContext,
                                                   ActionSelectionAid selectionAidMethod,
                                                   boolean showDisabledActions,
                                                   @Nullable Runnable disposeCallback,
                                                   int maxRowCount);

  @NotNull
  public ListPopup createActionGroupPopup(@Nls(capitalization = Nls.Capitalization.Title) String title,
                                          @NotNull ActionGroup actionGroup,
                                          @NotNull DataContext dataContext,
                                          boolean showDisabledActions,
                                          @Nullable Runnable disposeCallback,
                                          int maxRowCount) {
    return createActionGroupPopup(title, actionGroup, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, showDisabledActions, disposeCallback, maxRowCount);
  }

  @NotNull
  public abstract ListPopup createActionGroupPopup(@Nls(capitalization = Nls.Capitalization.Title) String title,
                                                   @NotNull ActionGroup actionGroup,
                                                   @NotNull DataContext dataContext,
                                                   boolean showNumbers,
                                                   boolean showDisabledActions,
                                                   boolean honorActionMnemonics,
                                                   @Nullable Runnable disposeCallback,
                                                   int maxRowCount,
                                                   @Nullable Condition<AnAction> preselectActionCondition);

  @NotNull
  public abstract ListPopup createActionGroupPopup(@Nls(capitalization = Nls.Capitalization.Title) String title,
                                                   @NotNull ActionGroup actionGroup,
                                                   @NotNull DataContext dataContext,
                                                   ActionSelectionAid selectionAidMethod,
                                                   boolean showDisabledActions,
                                                   @Nullable Runnable disposeCallback,
                                                   int maxRowCount,
                                                   @Nullable Condition<AnAction> preselectActionCondition,
                                                   @Nullable String actionPlace);

  /**
   * @deprecated use {@link #createListPopup(ListPopupStep)} instead ({@code step} must be a ListPopupStep in any case)
   */
  @NotNull
  public abstract ListPopup createWizardStep(@NotNull PopupStep step);

  /**
   * Creates a custom list popup with the specified step.
   *
   * @param step the custom step for the list popup.
   * @return the popup instance.
   */
  @NotNull
  public abstract ListPopup createListPopup(@NotNull ListPopupStep step);

  /**
   * Creates a custom list popup with the specified step.
   *
   * @param step        the custom step for the list popup.
   * @param maxRowCount the number of visible rows to show in the popup (if the popup has more items,
   *                    a scrollbar will be displayed).
   * @return the popup instance.
   * @since 14.1
   */
  @NotNull
  public abstract ListPopup createListPopup(@NotNull ListPopupStep step, int maxRowCount);

  @NotNull
  public abstract TreePopup createTree(JBPopup parent, @NotNull TreePopupStep step, Object parentValue);
  @NotNull
  public abstract TreePopup createTree(@NotNull TreePopupStep step);

  @NotNull
  public abstract ComponentPopupBuilder createComponentPopupBuilder(@NotNull JComponent content, @Nullable JComponent preferableFocusComponent);

  /**
   * Returns the location where a popup with the specified data context is displayed.
   *
   * @param dataContext the data context from which the location is determined.
   * @return location as close as possible to the action origin. Method has special handling of
   *         the following components:<br>
   *         - caret offset for editor<br>
   *         - current selected node for tree<br>
   *         - current selected row for list<br>
   */
  @NotNull
  public abstract RelativePoint guessBestPopupLocation(@NotNull DataContext dataContext);

  /**
   * Returns the location where a popup invoked from the specified editor should be displayed.
   *
   * @param editor the editor over which the popup is shown.
   * @return location as close as possible to the action origin.
   */
  @NotNull
  public abstract RelativePoint guessBestPopupLocation(@NotNull Editor editor);

  /**
   * @param editor the editor over which the popup is shown.
   * @return true if popup location is located in visible area
   *         false if center would be suggested instead
   */
  public abstract boolean isBestPopupLocationVisible(@NotNull Editor editor);

  public abstract Point getCenterOf(JComponent container, JComponent content);
  
  @NotNull
  public abstract List<JBPopup> getChildPopups(@NotNull Component parent);

  public abstract boolean isPopupActive();

  @NotNull
  public abstract BalloonBuilder createBalloonBuilder(@NotNull JComponent content);

  @NotNull
  public abstract BalloonBuilder createDialogBalloonBuilder(@NotNull JComponent content, String title);

  @NotNull
  public abstract BalloonBuilder createHtmlTextBalloonBuilder(@NotNull String htmlContent, @Nullable Icon icon, Color fillColor, @Nullable HyperlinkListener listener);

  @NotNull
  public abstract BalloonBuilder createHtmlTextBalloonBuilder(@NotNull String htmlContent, MessageType messageType, @Nullable HyperlinkListener listener);

  @NotNull
  public abstract JBPopup createMessage(String text);

  @Nullable
  public abstract Balloon getParentBalloonFor(@Nullable Component c);

}
