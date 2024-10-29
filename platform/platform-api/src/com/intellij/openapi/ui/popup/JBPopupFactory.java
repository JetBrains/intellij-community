// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.ui.popup;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.Html;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.util.List;
import java.util.function.Function;

/**
 * Factory class for creating popup chooser windows (similar to the Code | Generate... popup) and various notifications/confirmations.
 * <p/>
 * <p>Types of popups in IntelliJ platform:</p>
 *
 * <h3>Lightweight</h3>
 * <p>Lightweight Swing components, located in the contained window's layered pane. Cannot extend beyond window bounds.</p>
 * <ul>
 *   <li>
 *     <p>{@link Balloon} interface (implemented in {@link com.intellij.ui.BalloonImpl BalloonImpl})</p>
 *     <p>Platform's lowest-level lightweight popup component. Supports title bar, shadow, callout pointer at specified side, animated
 *        showing/hiding, fade-out after delay, two layers for positioning, hiding on condition (mouse move, mouse click, key press),
 *        custom mouse click handler, custom action buttons, notification about showing/hiding.</p>
 *     <p>Usually created via {@link JBPopupFactory#createBalloonBuilder}.</p>
 *   </li>
 *   <li>
 *     <p>{@link com.intellij.ide.IdeTooltip IdeTooltip} and {@link com.intellij.ide.IdeTooltipManager IdeTooltipManager}</p>
 *     <p>Subsystem which replaces (by default) Swing's ToolTipManager. Allows to define a custom mouse hover tooltip for any UI
 *        component. Tooltip can also be explicitly requested to show in the given location.</p>
 *     <p>Uses balloons to show tooltips internally (but doesn't expose this logic).</p>
 *   </li>
 * </ul>
 *
 * <h3>Heavyweight</h3>
 * <p>Components using a separate (OS-recognized) window. Can have location and size not limited by the parent window.</p>
 * <ul>
 *   <li>
 *     <p>{@link JBPopup} interface (implemented in {@link com.intellij.ui.popup.AbstractPopup AbstractPopup})</p>
 *     <p>Platform's lowest-level heavyweight popup component. Supports title and footer (ad) bar, resizing using mouse, fitting size to
 *        screen bounds, notification about showing/hiding/resizing/moving, hiding on condition (mouse exit, mouse click, key press,
 *        window deactivation), speed search, saving and restoring previously used location and size, custom keyboard actions for the
 *        content, custom settings/pin actions.</p>
 *     <p>Usually created via {@link JBPopupFactory#createComponentPopupBuilder}.</p>
 *   </li>
 * </ul>
 *
 * <h3>Combined (lightweight/heavyweight)</h3>
 * <ul>
 *   <li>
 *     <p>{@link com.intellij.ui.Hint Hint} interface (implemented in {@link com.intellij.ui.LightweightHint LightweightHint})</p>
 *     <p>By default, if content fits the layered pane, tries to use lightweight components to display it (either using
 *        {@link com.intellij.ide.IdeTooltipManager IdeTooltipManager}, or by directly adding content to the layered pane).
 *        If the content doesn't fit, uses heavyweight approach (via {@link JBPopup}).</p>
 *     <p>Usually created directly (via constructor).</p>
 *   </li>
 *   <li>
 *     <p>{@link HintManager}</p>
 *     <p>Mostly used for showing hints in editor (works via {@link com.intellij.ui.LightweightHint LightweightHint}), but also has a method to show a hint at arbitrary
 *        location (works via {@link JBPopup}). Supports hiding of hints on additional conditions (caret movement, scrolling in editor, document
 *        change, showing of another hint), update of hint position on scrolling in editor, hiding hint after delay. The class also has
 *        utility methods to calculate appropriate hint position for the given location in editor coordinates
 *        (offset/LogicalPosition/VisualPosition), and to show simple text hints.</p>
 *   </li>
 * </ul>
 *
 * @author mike
 */
public abstract class JBPopupFactory {
  /**
   * Returns the popup factory instance.
   *
   * @return the popup factory instance.
   */
  public static JBPopupFactory getInstance() {
    return ApplicationManager.getApplication().getService(JBPopupFactory.class);
  }

  public abstract @NotNull <T> IPopupChooserBuilder<T> createPopupChooserBuilder(@NotNull List<? extends T> list);

  public @NotNull PopupChooserBuilder createPopupChooserBuilder(@NotNull JTable table) {
    return new PopupChooserBuilder(table);
  }

  /**
   * @deprecated Please use {@link #createPopupChooserBuilder(List)} instead
  */
  @Deprecated
  public @NotNull <T> PopupChooserBuilder<T> createListPopupBuilder(@NotNull JList<T> list) {
    return new PopupChooserBuilder<>(list);
  }

  /**
   * Creates a popup with the specified title and two options, Yes and No.
   *
   * @param title the title of the popup.
   * @param onYes the runnable which is executed when the Yes option is selected.
   * @param defaultOptionIndex the index of the option which is selected by default.
   * @return the popup instance.
   */
  public abstract @NotNull ListPopup createConfirmation(@PopupTitle String title, Runnable onYes, int defaultOptionIndex);

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
  public abstract @NotNull ListPopup createConfirmation(@PopupTitle String title,
                                               @NlsContexts.Label String yesText,
                                               @NlsContexts.Label String noText,
                                               Runnable onYes,
                                               int defaultOptionIndex);

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
  public abstract @NotNull ListPopup createConfirmation(@PopupTitle String title,
                                               @NlsContexts.Label String yesText,
                                               @NlsContexts.Label String noText,
                                               Runnable onYes,
                                               Runnable onNo,
                                               int defaultOptionIndex);

  /**
   * @deprecated use {@link #createActionsStep(ActionGroup, DataContext, String, boolean, boolean, String, Component, boolean, int, boolean)}
   */
  @Deprecated(forRemoval = true)
  public @NotNull ListPopupStep createActionsStep(@NotNull ActionGroup actionGroup,
                                         @NotNull DataContext dataContext,
                                         boolean showNumbers,
                                         boolean showDisabledActions,
                                         @PopupTitle String title,
                                         Component component,
                                         boolean honorActionMnemonics,
                                         int defaultOptionIndex,
                                         boolean autoSelectionEnabled) {
    return createActionsStep(actionGroup, dataContext, null, showNumbers, showDisabledActions, title, component, honorActionMnemonics, defaultOptionIndex, autoSelectionEnabled);
  }

  public abstract @NotNull ListPopupStep createActionsStep(@NotNull ActionGroup actionGroup,
                                                           @NotNull DataContext dataContext,
                                                           @Nullable String actionPlace,
                                                           boolean showNumbers,
                                                           boolean showDisabledActions,
                                                           @PopupTitle String title,
                                                           Component component,
                                                           boolean honorActionMnemonics,
                                                           int defaultOptionIndex,
                                                           boolean autoSelectionEnabled);

  public @NotNull RelativePoint guessBestPopupLocation(@NotNull AnAction action, @NotNull AnActionEvent event) {
    return guessBestPopupLocation(event.getDataContext());
  }

  public abstract @NotNull RelativePoint guessBestPopupLocation(@NotNull JComponent component);

  public boolean isChildPopupFocused(@Nullable Component parent) {
    return getChildFocusedPopup(parent) != null;
  }

  public JBPopup getChildFocusedPopup(@Nullable Component parent) {
    if (parent == null) return null;
    List<JBPopup> popups = getChildPopups(parent);
    for (JBPopup each : popups) {
      if (each.isFocused()) return each;
      if (each.isDisposed()) continue;
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
  public @NotNull ListPopup createActionGroupPopup(@Nullable @PopupTitle String title,
                                          @NotNull ActionGroup actionGroup,
                                          @NotNull DataContext dataContext,
                                          ActionSelectionAid selectionAidMethod,
                                          boolean showDisabledActions) {
    return createActionGroupPopup(title,
                                  actionGroup,
                                  dataContext,
                                  selectionAidMethod,
                                  showDisabledActions,
                                  null,
                                  -1,
                                  null,
                                  null);
  }

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
  public @NotNull ListPopup createActionGroupPopup(@PopupTitle String title,
                                          @NotNull ActionGroup actionGroup,
                                          @NotNull DataContext dataContext,
                                          ActionSelectionAid selectionAidMethod,
                                          boolean showDisabledActions,
                                          @Nullable String actionPlace) {
    return createActionGroupPopup(title, actionGroup, dataContext, selectionAidMethod, showDisabledActions, null, -1, null, actionPlace);
  }

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
  public @NotNull ListPopup createActionGroupPopup(@PopupTitle String title,
                                          @NotNull ActionGroup actionGroup,
                                          @NotNull DataContext dataContext,
                                          ActionSelectionAid selectionAidMethod,
                                          boolean showDisabledActions,
                                          Runnable disposeCallback,
                                          int maxRowCount) {
    return createActionGroupPopup(title, actionGroup, dataContext, selectionAidMethod, showDisabledActions, disposeCallback, maxRowCount, null, null);
  }

  public @NotNull ListPopup createActionGroupPopup(@PopupTitle String title,
                                                   @NotNull ActionGroup actionGroup,
                                                   @NotNull DataContext dataContext,
                                                   boolean showDisabledActions,
                                                   @Nullable Runnable disposeCallback,
                                                   int maxRowCount) {
    return createActionGroupPopup(title, actionGroup, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, showDisabledActions, disposeCallback, maxRowCount);
  }

  public abstract @NotNull ListPopup createActionGroupPopup(@PopupTitle String title,
                                                            @NotNull ActionGroup actionGroup,
                                                            @NotNull DataContext dataContext,
                                                            boolean showNumbers,
                                                            boolean showDisabledActions,
                                                            boolean honorActionMnemonics,
                                                            @Nullable Runnable disposeCallback,
                                                            int maxRowCount,
                                                            @Nullable Condition<? super AnAction> preselectCondition);

  public abstract @NotNull ListPopup createActionGroupPopup(@PopupTitle String title,
                                                            @NotNull ActionGroup actionGroup,
                                                            @NotNull DataContext dataContext,
                                                            ActionSelectionAid aid,
                                                            boolean showDisabledActions,
                                                            @Nullable Runnable disposeCallback,
                                                            int maxRowCount,
                                                            @Nullable Condition<? super AnAction> preselectCondition,
                                                            @Nullable String actionPlace);

  /**
   * Creates a custom list popup with the specified step.
   *
   * @param step the custom step for the list popup.
   * @return the popup instance.
   */
  public abstract @NotNull ListPopup createListPopup(@NotNull ListPopupStep step);

  /**
   * Creates a custom list popup with the specified step.
   *
   * @param step        the custom step for the list popup.
   * @param maxRowCount the number of visible rows to show in the popup (if the popup has more items,
   *                    a scrollbar will be displayed).
   * @return the popup instance.
   */
  public abstract @NotNull ListPopup createListPopup(@NotNull ListPopupStep step, int maxRowCount);

  public abstract @NotNull ListPopup createListPopup(@NotNull Project project,
                                                     @NotNull ListPopupStep step,
                                                     @NotNull Function<? super ListCellRenderer, ? extends ListCellRenderer> cellRendererProducer);

  public abstract @NotNull TreePopup createTree(JBPopup parent, @NotNull TreePopupStep step, Object parentValue);

  public abstract @NotNull TreePopup createTree(@NotNull TreePopupStep step);

  public abstract @NotNull ComponentPopupBuilder createComponentPopupBuilder(@NotNull JComponent content,
                                                                             @Nullable JComponent preferableFocusComponent);

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
  public abstract @NotNull RelativePoint guessBestPopupLocation(@NotNull DataContext dataContext);

  /**
   * Returns the location where a popup invoked from the specified editor should be displayed.
   *
   * @param editor the editor over which the popup is shown.
   * @return location as close as possible to the action origin.
   */
  public abstract @NotNull RelativePoint guessBestPopupLocation(@NotNull Editor editor);

  /**
   * @param editor the editor over which the popup is shown.
   * @return true if popup location is located in visible area
   *         false if center would be suggested instead
   */
  public abstract boolean isBestPopupLocationVisible(@NotNull Editor editor);

  public abstract Point getCenterOf(JComponent container, JComponent content);

  public abstract @NotNull List<JBPopup> getChildPopups(@NotNull Component parent);

  public abstract boolean isPopupActive();

  public abstract @NotNull BalloonBuilder createBalloonBuilder(@NotNull JComponent content);

  public abstract @NotNull BalloonBuilder createDialogBalloonBuilder(@NotNull JComponent content, @PopupTitle String title);

  public @NotNull BalloonBuilder createHtmlTextBalloonBuilder(@NotNull @NlsContexts.PopupContent String htmlContent, @Nullable Icon icon, Color fillColor, @Nullable HyperlinkListener listener) {
    return createHtmlTextBalloonBuilder(htmlContent, icon, null, fillColor, listener);
  }

  public abstract @NotNull BalloonBuilder createHtmlTextBalloonBuilder(@NotNull @NlsContexts.PopupContent String htmlContent, @Nullable Icon icon, Color textColor, Color fillColor, @Nullable HyperlinkListener listener);

  public abstract @NotNull BalloonBuilder createHtmlTextBalloonBuilder(@NotNull @NlsContexts.PopupContent Html html, @Nullable Icon icon, Color textColor, Color fillColor, @Nullable HyperlinkListener listener);

  public abstract @NotNull BalloonBuilder createHtmlTextBalloonBuilder(@NotNull @NlsContexts.PopupContent String htmlContent, MessageType messageType, @Nullable HyperlinkListener listener);

  public abstract @NotNull JBPopup createMessage(@NlsContexts.PopupContent String text);

  public abstract @Nullable Balloon getParentBalloonFor(@Nullable Component c);

  protected abstract @NotNull <T> PopupChooserBuilder.PopupComponentAdapter<T> createPopupComponentAdapter(@NotNull PopupChooserBuilder<T> builder, @NotNull JList<T> list);
  protected abstract @NotNull <T> PopupChooserBuilder.PopupComponentAdapter<T> createPopupComponentAdapter(@NotNull PopupChooserBuilder<T> builder, @NotNull JTree tree);
  protected abstract @NotNull <T> PopupChooserBuilder.PopupComponentAdapter<T> createPopupComponentAdapter(@NotNull PopupChooserBuilder<T> builder, @NotNull JTable table);
}
