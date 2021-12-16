// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.SmartFMap;
import com.intellij.util.SmartList;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.intellij.openapi.util.NlsActions.ActionDescription;
import static com.intellij.openapi.util.NlsActions.ActionText;

/**
 * Represents an entity that has a state, a presentation and can be performed.
 *
 * For an action to be useful, you need to implement {@link AnAction#actionPerformed}
 * and optionally to override {@link AnAction#update}. By overriding the
 * {@link AnAction#update} method you can dynamically change action's presentation
 * depending on the place (for more information on places see {@link com.intellij.openapi.actionSystem.ActionPlaces}.
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
 * @see com.intellij.openapi.actionSystem.ActionPlaces
 * @see com.intellij.openapi.project.DumbAwareAction
 */
public abstract class AnAction implements PossiblyDumbAware {
  private static final Logger LOG = Logger.getInstance(AnAction.class);

  public static final Key<List<AnAction>> ACTIONS_KEY = Key.create("AnAction.shortcutSet");
  public static final AnAction[] EMPTY_ARRAY = new AnAction[0];

  private Presentation myTemplatePresentation;
  @NotNull
  private ShortcutSet myShortcutSet = CustomShortcutSet.EMPTY;
  private boolean myEnabledInModalContext;

  private boolean myIsDefaultIcon = true;
  private boolean myWorksInInjected;
  private SmartFMap<String, Supplier<String>> myActionTextOverrides = SmartFMap.emptyMap();
  private List<Supplier<@Nls String>> mySynonyms = Collections.emptyList();

  /**
   * Creates a new action with its text, description and icon set to {@code null}.
   */
  public AnAction(){
    // avoid eagerly creating template presentation
  }

  /**
   * Creates a new action with {@code icon} provided. Its text, description set to {@code null}.
   *
   * @param icon Default icon to appear in toolbars and menus (Note some platform don't have icons in menu).
   */
  public AnAction(Icon icon){
    this(Presentation.NULL_STRING, Presentation.NULL_STRING, icon);
  }

  /**
   * Creates a new action with the specified text. Description and icon are
   * set to {@code null}.
   *
   * @param text Serves as a tooltip when the presentation is a button and the name of the
   *  menu item when the presentation is a menu item.
   */
  public AnAction(@Nullable @ActionText String text) {
    this(text, null, null);
  }

  /**
   * Creates a new action with the specified text. Description and icon are
   * set to {@code null}.
   *
   * @param dynamicText Serves as a tooltip when the presentation is a button and the name of the
   * menu item when the presentation is a menu item.
   *
   *  Use it if you need to localize action text.
   */
  public AnAction(@NotNull Supplier<@ActionText String> dynamicText) {
    this(dynamicText, Presentation.NULL_STRING, null);
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
  public AnAction(@Nullable @ActionText String text,
                  @Nullable @ActionDescription String description,
                  @Nullable Icon icon) {
    this(() -> text, () -> description, icon);
  }

  /**
   * Constructs a new action with the specified dynamicText, dynamicDescription and icon.
   *
   * @param dynamicText Serves as a tooltip when the presentation is a button and the name of the
   *  menu item when the presentation is a menu item. Use it if you need to localize action text.
   *
   * @param icon Action's icon
   */
  public AnAction(@NotNull Supplier<@ActionText String> dynamicText, @Nullable Icon icon) {
    this(dynamicText, Presentation.NULL_STRING, icon);
  }

  /**
   * Constructs a new action with the specified dynamicText, dynamicDescription and icon.
   *
   * @param dynamicText Serves as a tooltip when the presentation is a button and the name of the
   *  menu item when the presentation is a menu item. Use it if you need to localize action text.
   *
   * @param dynamicDescription Describes current action, this dynamicDescription will appear on
   *  the status bar when presentation has focus. Use it if you need to localize description.
   *
   * @param icon Action's icon
   */
  public AnAction(@NotNull Supplier<@ActionText String> dynamicText,
                  @NotNull Supplier<@ActionDescription String> dynamicDescription,
                  @Nullable Icon icon) {
    Presentation presentation = getTemplatePresentation();
    presentation.setText(dynamicText);
    presentation.setDescription(dynamicDescription);
    presentation.setIcon(icon);
  }

  /**
   * Returns the shortcut set associated with this action.
   *
   * @return shortcut set associated with this action
   */
  @NotNull
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
  public final void registerCustomShortcutSet(@NotNull ShortcutSet shortcutSet, @Nullable JComponent component) {
    registerCustomShortcutSet(shortcutSet, component, null);
  }

  public final void registerCustomShortcutSet(int keyCode, @JdkConstants.InputEventMask int modifiers, @Nullable JComponent component) {
    registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(keyCode, modifiers)), component);
  }

  public final void registerCustomShortcutSet(@NotNull ShortcutSet shortcutSet, @Nullable JComponent component, @Nullable Disposable parentDisposable) {
    setShortcutSet(shortcutSet);
    registerCustomShortcutSet(component, parentDisposable);
  }

  public final void registerCustomShortcutSet(@Nullable JComponent component, @Nullable Disposable parentDisposable) {
    if (component == null) return;
    List<AnAction> actionList = ComponentUtil.getClientProperty(component, ACTIONS_KEY);
    if (actionList == null) {
      List<AnAction> value = actionList = new SmartList<>();
      ComponentUtil.putClientProperty(component, ACTIONS_KEY, value);
    }
    if (!actionList.contains(this)) {
      actionList.add(this);
    }

    if (parentDisposable != null) {
      Disposer.register(parentDisposable, () -> unregisterCustomShortcutSet(component));
    }
  }

  public final void unregisterCustomShortcutSet(@NotNull JComponent component) {
    List<AnAction> actionList = ComponentUtil.getClientProperty(component, ACTIONS_KEY);
    if (actionList != null) {
      actionList.remove(this);
    }
  }

  /**
   * Copies template presentation and shortcuts set from {@code sourceAction}.
   * Consider using {@link com.intellij.openapi.actionSystem.ex.ActionUtil#copyFrom(AnAction, String)} instead.
   */
  public final void copyFrom(@NotNull AnAction sourceAction) {
    Presentation sourcePresentation = sourceAction.getTemplatePresentation();
    Presentation presentation = getTemplatePresentation();
    presentation.copyFrom(sourcePresentation);
    copyShortcutFrom(sourceAction);
  }

  public final void copyShortcutFrom(@NotNull AnAction sourceAction) {
    setShortcutSet(sourceAction.getShortcutSet());
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
   * Override with true returned if your action displays text in a smaller font (same as toolbar combobox font) when placed in the toolbar
   */
  public boolean useSmallerFontForTextInToolbar() {
    return false;
  }

  /**
   * Updates the state of the action. Default implementation does nothing.
   * Override this method to provide the ability to dynamically change action's
   * state and(or) presentation depending on the context (For example
   * when your action state depends on the selection you can check for
   * selection and change the state accordingly).<p></p>
   *
   * This method can be called frequently, and on UI thread.
   * This means that this method is supposed to work really fast,
   * no real work should be done at this phase. For example, checking selection in a tree or a list,
   * is considered valid, but working with a file system or PSI (especially resolve) is not.
   * If you cannot determine the state of the action fast enough,
   * you should do it in the {@link #actionPerformed(AnActionEvent)} method and notify
   * the user that action cannot be executed if it's the case.<p></p>
   *
   * If the action is added to a toolbar, its "update" can be called twice a second, but only if there was
   * any user activity or a focus transfer. If your action's availability is changed
   * in absence of any of these events, please call {@code ActivityTracker.getInstance().inc()} to notify
   * action subsystem to update all toolbar actions when your subsystem's determines that its actions' visibility might be affected.
   *
   * @param e Carries information on the invocation place and data available
   */
  public void update(@NotNull AnActionEvent e) {
  }

  /**
   * Same as {@link #update(AnActionEvent)} but is calls immediately before actionPerformed() as final check guard.
   * Default implementation delegates to {@link #update(AnActionEvent)}.
   *
   * @param e Carries information on the invocation place and data available
   */
  public void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
    update(e);
  }

  /**
   * Returns a template presentation that will be used
   * as a template for created presentations.
   *
   * @return template presentation
   */
  public final @NotNull Presentation getTemplatePresentation() {
    Presentation presentation = myTemplatePresentation;
    if (presentation == null) {
      presentation = createTemplatePresentation();
      LOG.assertTrue(presentation.isTemplate(), "Not a template presentation");
      myTemplatePresentation = presentation;
    }
    return presentation;
  }

  @NotNull
  Presentation createTemplatePresentation() {
    return Presentation.newTemplatePresentation();
  }

  /**
   * Implement this method to provide your action handler.
   *
   * @param e Carries information on the invocation place
   */
  public abstract void actionPerformed(@NotNull AnActionEvent e);

  protected void setShortcutSet(@NotNull ShortcutSet shortcutSet) {
    if (myShortcutSet != shortcutSet &&
        myShortcutSet != CustomShortcutSet.EMPTY &&
        LoadingState.PROJECT_OPENED.isOccurred()) {
      ActionManager actionManager = ApplicationManager.getApplication().getServiceIfCreated(ActionManager.class);
      if (actionManager != null && actionManager.getId(this) != null) {
        LOG.warn("ShortcutSet of global AnActions should not be changed outside of KeymapManager.\n" +
                 "This is likely not what you wanted to do. Consider setting shortcut in keymap defaults, inheriting from other action " +
                 "using `use-shortcut-of` or wrapping with EmptyAction.wrap().", new Throwable(this.toString()));
      }
    }
    myShortcutSet = shortcutSet;
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

  /**
   * Enables automatic detection of injected fragments in editor. Values in DataContext, passed to the action, like EDITOR, PSI_FILE
   * will refer to an injected fragment, if caret is currently positioned on it.
   */
  public void setInjectedContext(boolean worksInInjected) {
    myWorksInInjected = worksInInjected;
  }

  public boolean isInInjectedContext() {
    return myWorksInInjected;
  }

  /** @deprecated not used anymore */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public boolean isTransparentUpdate() {
    return this instanceof TransparentUpdate;
  }

  /**
   * @deprecated unused
   */
  @Deprecated
  public boolean startInTransaction() {
    return false;
  }

  public void addTextOverride(@NotNull String place, @NotNull String text) {
    addTextOverride(place, () -> text);
  }

  public void addTextOverride(@NotNull String place, @NotNull Supplier<String> text) {
    myActionTextOverrides = myActionTextOverrides.plus(place, text);
  }

  @ApiStatus.Internal
  public void copyActionTextOverride(@NotNull String fromPlace, @NotNull String toPlace, String id) {
    Supplier<String> value = myActionTextOverrides.get(fromPlace);
    if (value == null) {
      LOG.error("Missing override-text for action " + id + " and place specified in use-text-of-place: " + fromPlace);
      return;
    }
    myActionTextOverrides = myActionTextOverrides.plus(toPlace, value);
  }



  @ApiStatus.Internal
  public void applyTextOverride(@NotNull AnActionEvent event) {
    applyTextOverride(event.getPlace(), event.getPresentation());
  }

  @ApiStatus.Internal
  public void applyTextOverride(@NotNull String place, @NotNull Presentation presentation) {
    Supplier<String> override = myActionTextOverrides.get(place);
    if (override != null) {
      presentation.setText(override);
    }
  }

  @ApiStatus.Internal
  protected void copyActionTextOverrides(AnAction targetAction) {
    for (String place : myActionTextOverrides.keySet()) {
      targetAction.addTextOverride(place, Objects.requireNonNull(myActionTextOverrides.get(place)));
    }
  }

  public void addSynonym(@NotNull Supplier<@Nls String> text) {
    if (mySynonyms == Collections.<Supplier<String>>emptyList()) {
      mySynonyms = new SmartList<>(text);
    }
    else {
      mySynonyms.add(text);
    }
  }

  public @NotNull List<Supplier<@Nls String>> getSynonyms() {
    return mySynonyms;
  }

  /** @deprecated not used anymore */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public interface TransparentUpdate {
  }

  @Nullable
  public static Project getEventProject(AnActionEvent e) {
    return e == null ? null : e.getData(CommonDataKeys.PROJECT);
  }

  @Override
  @Nls
  public String toString() {
    return getTemplatePresentation().toString();
  }

  /**
   * Returns default action text.
   * This method must be overridden in case template presentation contains user data like Project name,
   * Run Configuration name, etc
   *
   * @return action presentable text without private user data
   */
  @Nullable
  @ActionText
  public String getTemplateText() {
    return getTemplatePresentation().getText();
  }
}
