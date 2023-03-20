// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.ReflectionUtil;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static com.intellij.openapi.util.NlsActions.ActionDescription;
import static com.intellij.openapi.util.NlsActions.ActionText;

/**
 * An action has a state, a presentation and can be performed.
 * <p>
 * For an action to be useful, implement {@link AnAction#actionPerformed}.
 * <p>
 * The same action can have various presentations.
 * To dynamically change the action's presentation depending on the place, override {@link AnAction#update}.
 * For more information on places, see {@link ActionPlaces}.
 * <pre>
 * public void update(AnActionEvent e) {
 *   if (e.getPlace().equals(ActionPlaces.MAIN_MENU)) {
 *     e.getPresentation().setText("My Menu item name");
 *   } else if (e.getPlace().equals(ActionPlaces.MAIN_TOOLBAR)) {
 *     e.getPresentation().setText("My Toolbar item name");
 *   }
 * }
 * </pre>
 *
 * @see AnActionEvent
 * @see Presentation
 * @see ActionPlaces
 * @see com.intellij.openapi.project.DumbAwareAction
 */
public abstract class AnAction implements PossiblyDumbAware, ActionUpdateThreadAware {
  private static final Logger LOG = Logger.getInstance(AnAction.class);

  public static final Key<List<AnAction>> ACTIONS_KEY = Key.create("AnAction.shortcutSet");
  public static final AnAction[] EMPTY_ARRAY = new AnAction[0];

  private Presentation myTemplatePresentation;
  private @NotNull ShortcutSet myShortcutSet = CustomShortcutSet.EMPTY;
  private boolean myEnabledInModalContext;

  private boolean myIsDefaultIcon = true;
  private boolean myWorksInInjected;
  private SmartFMap<String, Supplier<String>> myActionTextOverrides = SmartFMap.emptyMap();
  private List<Supplier<@Nls String>> mySynonyms = Collections.emptyList();

  private Boolean myUpdateNotOverridden;

  /**
   * Creates a new action with its text, description and icon set to {@code null}.
   */
  public AnAction() {
    // avoid eagerly creating template presentation
  }

  /**
   * Creates a new action with the given {@code icon}, but without text or description.
   *
   * @param icon the default icon to appear in toolbars and menus. Note that some platforms don't have icons in the menu.
   */
  public AnAction(@Nullable Icon icon) {
    this(Presentation.NULL_STRING, Presentation.NULL_STRING, icon);
  }

  /**
   * Creates a new action with the given text, but without description or icon.
   *
   * @param text serves as a tooltip when the presentation is a button,
   *             and the name of the menu item when the presentation is a menu item (with mnemonic)
   */
  public AnAction(@Nullable @ActionText String text) {
    this(text, null, null);
  }

  /**
   * Creates a new action with the given text, but without description or icon.
   * Use this variant if you need to localize the action text.
   *
   * @param dynamicText serves as a tooltip when the presentation is a button,
   *                    and the name of the menu item when the presentation is a menu item (with mnemonic)
   */
  public AnAction(@NotNull Supplier<@ActionText String> dynamicText) {
    this(dynamicText, Presentation.NULL_STRING, null);
  }

  /**
   * Creates a new action with the given text, description and icon.
   *
   * @param dynamicText serves as a tooltip when the presentation is a button,
   *                    and the name of the menu item when the presentation is a menu item (with mnemonic)
   * @param description describes the current action,
   *                    this description will appear on the status bar when the presentation has the focus
   * @param icon        the action's icon
   */
  public AnAction(@Nullable @ActionText String text,
                  @Nullable @ActionDescription String description,
                  @Nullable Icon icon) {
    this(() -> text, () -> description, icon);
  }

  /**
   * Creates a new action with the given text, description and icon.
   * Use this variant if you need to localize the action text.
   *
   * @param dynamicText serves as a tooltip when the presentation is a button,
   *                    and the name of the menu item when the presentation is a menu item (with mnemonic)
   * @param icon        the action's icon
   */
  public AnAction(@NotNull Supplier<@ActionText String> dynamicText, @Nullable Icon icon) {
    this(dynamicText, Presentation.NULL_STRING, icon);
  }

  /**
   * Creates a new action with the given text, description and icon.
   * Use this variant if you need to localize the action text or the description.
   *
   * @param dynamicText        serves as a tooltip when the presentation is a button,
   *                           and the name of the menu item when the presentation is a menu item (with mnemonic)
   * @param dynamicDescription describes the current action,
   *                           this description will appear on the status bar when the presentation has the focus
   * @param icon               the action's icon
   */
  public AnAction(@NotNull Supplier<@ActionText String> dynamicText,
                  @NotNull Supplier<@ActionDescription String> dynamicDescription,
                  @Nullable Icon icon) {
    Presentation presentation = getTemplatePresentation();
    presentation.setText(dynamicText);
    presentation.setDescription(dynamicDescription);
    presentation.setIcon(icon);
  }

  @Override
  public boolean isDumbAware() {
    if (PossiblyDumbAware.super.isDumbAware()) {
      return true;
    }
    return updateNotOverridden();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    if (this instanceof UpdateInBackground && ((UpdateInBackground)this).isUpdateInBackground()) {
      return ActionUpdateThread.BGT;
    }
    if (updateNotOverridden()) {
      return ActionUpdateThread.BGT;
    }
    return ActionUpdateThreadAware.super.getActionUpdateThread();
  }

  private boolean updateNotOverridden() {
    if (myUpdateNotOverridden != null) {
      return myUpdateNotOverridden;
    }
    Class<?> declaringClass = ReflectionUtil.getMethodDeclaringClass(getClass(), "update", AnActionEvent.class);
    myUpdateNotOverridden = AnAction.class.equals(declaringClass);
    return myUpdateNotOverridden;
  }

  /** Returns the set of shortcuts associated with this action. */
  public final @NotNull ShortcutSet getShortcutSet() {
    return myShortcutSet;
  }

  /**
   * Registers a set of shortcuts that will be processed when the specified component is the ancestor of the focused component.
   * Note that the action doesn't have to be registered in the action manager in order for that shortcut to work.
   *
   * @param shortcutSet the shortcuts for the action
   * @param component   the component for which the shortcuts will be active
   */
  public final void registerCustomShortcutSet(@NotNull ShortcutSet shortcutSet, @Nullable JComponent component) {
    registerCustomShortcutSet(shortcutSet, component, null);
  }

  public final void registerCustomShortcutSet(int keyCode, @JdkConstants.InputEventMask int modifiers, @Nullable JComponent component) {
    registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(keyCode, modifiers)), component);
  }

  public final void registerCustomShortcutSet(@NotNull ShortcutSet shortcutSet,
                                              @Nullable JComponent component,
                                              @Nullable Disposable parentDisposable) {
    setShortcutSet(shortcutSet);
    registerCustomShortcutSet(component, parentDisposable);
  }

  public final void registerCustomShortcutSet(@Nullable JComponent component, @Nullable Disposable parentDisposable) {
    if (component == null) return;
    List<AnAction> actionList = ComponentUtil.getClientProperty(component, ACTIONS_KEY);
    if (actionList == null) {
      List<AnAction> value = new CopyOnWriteArrayList<>();
      ComponentUtil.putClientProperty(component, ACTIONS_KEY, value);
      actionList = Objects.requireNonNullElse(ComponentUtil.getClientProperty(component, ACTIONS_KEY), value);
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
   * Copies the template presentation and the set of shortcuts from {@code sourceAction}.
   * Consider using {@link com.intellij.openapi.actionSystem.ex.ActionUtil#copyFrom(AnAction, String)} instead.
   */
  public final void copyFrom(@NotNull AnAction sourceAction) {
    Presentation sourcePresentation = sourceAction.getTemplatePresentation();
    Presentation presentation = getTemplatePresentation();
    boolean allFlags = this instanceof ActionGroup && sourceAction instanceof ActionGroup;
    presentation.copyFrom(sourcePresentation, null, allFlags);
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
   * Override with true returned if your action has to display its text along with the icon when placed in the toolbar.
   */
  public boolean displayTextInToolbar() {
    return false;
  }

  /**
   * Override with true returned if your action displays text in a smaller font (same as toolbar combobox font) when placed in the toolbar.
   */
  public boolean useSmallerFontForTextInToolbar() {
    return false;
  }

  /**
   * Updates the presentation of the action.
   * The default implementation does nothing.
   * <p>
   * Override this method to dynamically change the action's state or presentation depending on the context.
   * For example, when your action state depends on the selection,
   * you can check for the selection and change the state accordingly.
   * <p>
   * This method can be called frequently and on the UI thread.
   * This means that this method is supposed to work really fast,
   * no real work should be done at this phase.
   * For example, checking the selection in a tree or a list is considered valid,
   * but working with a file system or PSI (especially resolve) is not.
   * If you cannot determine the state of the action fast enough,
   * you should do it in the {@link #actionPerformed(AnActionEvent)} method
   * and notify the user that the action cannot be executed if it's the case.
   * <p>
   * If the action is added to a toolbar, its {@code update} method can be called twice a second,
   * but only if there was any user activity or a focus transfer.
   * If your action's availability is independent from these events,
   * call {@code ActivityTracker.getInstance().inc()}
   * to notify the action subsystem to update all toolbar actions
   * when your subsystem's determines that its actions' visibility might be affected.
   *
   * @see #getActionUpdateThread()
   */
  public void update(@NotNull AnActionEvent e) {
  }

  /**
   * Updates the presentation of the action just before the {@link #actionPerformed(AnActionEvent)} method is called.
   * The default implementation simply delegates to the {@link #update(AnActionEvent)} method.
   * <p/>
   * It is called on the UI thread with all data in the provided {@link DataContext} instance.
   *
   * @see #actionPerformed(AnActionEvent)
   */
  public void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
    update(e);
  }

  /**
   * Returns the template presentation of the action that is cloned each time
   * a new presentation of the action is needed.
   */
  public final @NotNull Presentation getTemplatePresentation() {
    Presentation presentation = myTemplatePresentation;
    if (presentation == null) {
      presentation = createTemplatePresentation();
      LOG.assertTrue(presentation.isTemplate(), "Not a template presentation");
      myTemplatePresentation = presentation;
      if (this instanceof ActionGroup) { // init group flags from deprecated methods
        //myTemplatePresentation.setPopupGroup(((ActionGroup)this).isPopup());
        myTemplatePresentation.setHideGroupIfEmpty(((ActionGroup)this).hideIfNoVisibleChildren());
        myTemplatePresentation.setDisableGroupIfEmpty(((ActionGroup)this).disableIfNoVisibleChildren());
      }
    }
    return presentation;
  }

  @NotNull
  Presentation createTemplatePresentation() {
    return Presentation.newTemplatePresentation();
  }

  /**
   * Performs the action logic.
   * <p>
   * It is called on the UI thread with all data in the provided {@link DataContext} instance.
   *
   * @see #beforeActionPerformedUpdate(AnActionEvent)
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
   *
   * @param isDefaultIconSet true if the icon is internal, false if the icon is customized by the user
   */
  public void setDefaultIcon(boolean isDefaultIconSet) {
    myIsDefaultIcon = isDefaultIconSet;
  }

  /**
   * Returns true if the action has an internal, not user-customized icon.
   *
   * @return true if the icon is internal, false if the icon is customized by the user.
   */
  public boolean isDefaultIcon() {
    return myIsDefaultIcon;
  }

  /**
   * Enables automatic detection of injected fragments in the editor.
   * Values that are passed to the action in its DataContext, like EDITOR or PSI_FILE,
   * will refer to an injected fragment if the caret is currently positioned on it.
   */
  public void setInjectedContext(boolean worksInInjected) {
    myWorksInInjected = worksInInjected;
  }

  public boolean isInInjectedContext() {
    return myWorksInInjected;
  }

  /** @deprecated not used anymore */
  @Deprecated(forRemoval = true)
  public boolean isTransparentUpdate() {
    return this instanceof TransparentUpdate;
  }

  /** @deprecated unused */
  @Deprecated(forRemoval = true)
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
  @Deprecated(forRemoval = true)
  public interface TransparentUpdate {
  }

  public static @Nullable Project getEventProject(AnActionEvent e) {
    return e == null ? null : e.getData(CommonDataKeys.PROJECT);
  }

  @Override
  public @Nls String toString() {
    return getTemplatePresentation().toString();
  }

  /**
   * Returns the default action text.
   * <p>
   * This method must be overridden if the template presentation contains user data
   * like the name of the project, of a run configuration, etc.
   *
   * @return action presentable text without private user data
   */
  public @Nullable @ActionText String getTemplateText() {
    return getTemplatePresentation().getText();
  }
}
