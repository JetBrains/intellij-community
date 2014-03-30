/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Represents an entity that has a state, a presentation and can be performed.
 *
 * For an action to be useful, you need to implement {@link AnAction#actionPerformed}
 * and optionally to override {@link com.intellij.openapi.actionSystem.AnAction#update}. By overriding the
 * {@link com.intellij.openapi.actionSystem.AnAction#update} method you can dynamically change action's presentation
 * depending on the place (for more information on places see {@link ActionPlaces}.
 *
 * The same action can have various presentations.
 *
 * <pre>
 *  public class MyAction extends AnAction {
 *    public MyAction() {
 *      // ...
 *    }
 *
 *    public void update(AnActionEvent e) {
 *      Presentation presentation = e.getPresentation();
 *      if (e.getPlace().equals(ActionPlaces.MAIN_MENU)) {
 *        presentation.setText("My Menu item name");
 *      } else if (e.getPlace().equals(ActionPlaces.MAIN_TOOLBAR)) {
 *        presentation.setText("My Toolbar item name");
 *      }
 *    }
 *
 *    public void actionPerformed(AnActionEvent e) { ... }
 *  }
 * </pre>
 *
 * @see AnActionEvent
 * @see Presentation
 * @see ActionPlaces
 */
public abstract class AnAction implements PossiblyDumbAware {
  public static final AnAction[] EMPTY_ARRAY = new AnAction[0];
  @NonNls public static final String ourClientProperty = "AnAction.shortcutSet";

  private Presentation myTemplatePresentation;
  private ShortcutSet myShortcutSet;
  private boolean myEnabledInModalContext;


  private static final ShortcutSet ourEmptyShortcutSet = new CustomShortcutSet();
  private boolean myIsDefaultIcon = true;
  private boolean myWorksInInjected;


  /**
   * Creates a new action with its text, description and icon set to <code>null</code>.
   */
  public AnAction(){
    this(null, null, null);
  }

  /**
   * Creates a new action with <code>icon</code> provided. Its text, description set to <code>null</code>.
   *
   * @param icon Default icon to appear in toolbars and menus (Note some platform don't have icons in menu).
   */
  public AnAction(Icon icon){
    this(null, null, icon);
  }

  /**
   * Creates a new action with the specified text. Description and icon are
   * set to <code>null</code>.
   *
   * @param text Serves as a tooltip when the presentation is a button and the name of the
   *  menu item when the presentation is a menu item.
   */
  public AnAction(@Nullable String text){
    this(text, null, null);
  }

  /**
   * Constructs a new action with the specified text, description and icon.
   *
   * @param text Serves as a tooltip when the presentation is a button and the name of the
   *  menu item when the presentation is a menu item
   *
   * @param description Describes current action, this description will appear on
   *  the status bar when presentation has focus
   *
   * @param icon Action's icon
   */
  public AnAction(@Nullable String text, @Nullable String description, @Nullable Icon icon){
    myShortcutSet = ourEmptyShortcutSet;
    myEnabledInModalContext = false;
    Presentation presentation = getTemplatePresentation();
    presentation.setText(text);
    presentation.setDescription(description);
    presentation.setIcon(icon);
  }

  /**
   * Returns the shortcut set associated with this action.
   *
   * @return shortcut set associated with this action
   */
  public final ShortcutSet getShortcutSet(){
    return myShortcutSet;
  }

  /**
   * Registers a set of shortcuts that will be processed when the specified component
   * is the ancestor of focused component. Note that the action doesn't have
   * to be registered in action manager in order for that shortcut to work.
   *
   * @param shortcutSet the shortcuts for the action.
   * @param component   the component for which the shortcuts will be active.
   */
  public final void registerCustomShortcutSet(@NotNull ShortcutSet shortcutSet, @Nullable JComponent component){
    myShortcutSet = shortcutSet;
    if (component != null){
      @SuppressWarnings("unchecked")
      List<AnAction> actionList = (List<AnAction>)component.getClientProperty(ourClientProperty);
      if (actionList == null){
        actionList = new SmartList<AnAction>();
        component.putClientProperty(ourClientProperty, actionList);
      }
      if (!actionList.contains(this)){
        actionList.add(this);
      }
    }
  }

  public final void registerCustomShortcutSet(int keyCode, @JdkConstants.InputEventMask int modifiers, @Nullable JComponent component) {
    registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(keyCode, modifiers)), component);
  }

  public final void registerCustomShortcutSet(@NotNull ShortcutSet shortcutSet, @NotNull final JComponent component, @NotNull Disposable parentDisposable) {
    registerCustomShortcutSet(shortcutSet, component);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        unregisterCustomShortcutSet(component);
      }
    });
  }

  public final void unregisterCustomShortcutSet(JComponent component){
    if (component != null){
      @SuppressWarnings("unchecked")
      List<AnAction> actionList = (List<AnAction>)component.getClientProperty(ourClientProperty);
      if (actionList != null){
        actionList.remove(this);
      }
    }
  }

  /**
   * Copies template presentation and shortcuts set from <code>sourceAction</code>.
   *
   * @param sourceAction cannot be <code>null</code>
   */
  public final void copyFrom(@NotNull AnAction sourceAction){
    Presentation sourcePresentation = sourceAction.getTemplatePresentation();
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(sourcePresentation.getIcon());
    presentation.setText(sourcePresentation.getTextWithMnemonic());
    presentation.setDescription(sourcePresentation.getDescription());
    copyShortcutFrom(sourceAction);
  }

  public final void copyShortcutFrom(@NotNull AnAction sourceAction) {
    myShortcutSet = sourceAction.myShortcutSet;
  }


  public final boolean isEnabledInModalContext() {
    return myEnabledInModalContext;
  }

  protected final void setEnabledInModalContext(boolean enabledInModalContext) {
    myEnabledInModalContext = enabledInModalContext;
  }

  /**
   * Override with true returned if your action has to display its text along with the icon when placed in the toolbar
   */
  public boolean displayTextInToolbar() {
    return false;
  }

  /**
   * Updates the state of the action. Default implementation does nothing.
   * Override this method to provide the ability to dynamically change action's
   * state and(or) presentation depending on the context (For example
   * when your action state depends on the selection you can check for
   * selection and change the state accordingly).
   * This method can be called frequently, for instance, if an action is added to a toolbar,
   * it will be updated twice a second. This means that this method is supposed to work really fast,
   * no real work should be done at this phase. For example, checking selection in a tree or a list,
   * is considered valid, but working with a file system is not. If you cannot understand the state of
   * the action fast you should do it in the {@link #actionPerformed(AnActionEvent)} method and notify
   * the user that action cannot be executed if it's the case.
   *
   * @param e Carries information on the invocation place and data available
   */
  public void update(AnActionEvent e) {
  }

  /**
   * Same as {@link #update(AnActionEvent)} but is calls immediately before actionPerformed() as final check guard.
   * Default implementation delegates to {@link #update(AnActionEvent)}.
   *
   * @param e Carries information on the invocation place and data available
   */
  public void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
    boolean worksInInjected = isInInjectedContext();
    e.setInjectedContext(worksInInjected);
    update(e);
    if (!e.getPresentation().isEnabled() && worksInInjected) {
      e.setInjectedContext(false);
      update(e);
    }
  }

  /**
   * Returns a template presentation that will be used
   * as a template for created presentations.
   *
   * @return template presentation
   */
  @NotNull
  public final Presentation getTemplatePresentation() {
    Presentation presentation = myTemplatePresentation;
    if (presentation == null){
      myTemplatePresentation = presentation = new Presentation();
    }
    return presentation;
  }

  /**
   * Implement this method to provide your action handler.
   *
   * @param e Carries information on the invocation place
   */
  public abstract void actionPerformed(AnActionEvent e);

  protected void setShortcutSet(ShortcutSet shortcutSet) {
    myShortcutSet = shortcutSet;
  }

  /**
   * @deprecated Use KeymapUtil.createTooltipText()
   */
  public static String createTooltipText(String s, @NotNull AnAction action) {
    throw new IncorrectOperationException("Please use KeymapUtil.createTooltipText()");
  }

  /**
   * Sets the flag indicating whether the action has an internal or a user-customized icon.
   * @param isDefaultIconSet true if the icon is internal, false if the icon is customized by the user.
   */
  public void setDefaultIcon(boolean isDefaultIconSet) {
    myIsDefaultIcon = isDefaultIconSet;
  }

  /**
   * Returns true if the action has an internal, not user-customized icon.
   * @return true if the icon is internal, false if the icon is customized by the user.
   */
  public boolean isDefaultIcon() {
    return myIsDefaultIcon;
  }

  public void setInjectedContext(boolean worksInInjected) {
    myWorksInInjected = worksInInjected;
  }

  public boolean isInInjectedContext() {
    return myWorksInInjected;
  }

  public boolean isTransparentUpdate() {
    return this instanceof TransparentUpdate;
  }

  @Override
  public boolean isDumbAware() {
    return this instanceof DumbAware;
  }

  public interface TransparentUpdate {
  }

  @Nullable
  public static Project getEventProject(AnActionEvent e) {
    return e == null ? null : e.getData(CommonDataKeys.PROJECT);
  }

  @Override
  public String toString() {
    return getTemplatePresentation().toString();
  }
}
