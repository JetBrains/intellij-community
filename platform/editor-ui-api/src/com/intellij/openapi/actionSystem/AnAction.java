// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.diagnostic.LoadingState;
import com.intellij.diagnostic.PluginException;
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
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static com.intellij.openapi.util.NlsActions.ActionDescription;
import static com.intellij.openapi.util.NlsActions.ActionText;

/**
 * An action has a state, a number of presentations and can be performed.
 * <p>
 * For an action to be useful, implement {@link #actionPerformed(AnActionEvent)}.
 * To alter how the action is presented in UI, implement {@link #update(AnActionEvent)}.
 * <p>
 * Actions have dedicated presentations wherever they are presented to the user.
 * A single action can be present in different toolbars, popups and menus on the screen at the same time.
 * The default presentation for each place is a copy of {@link #getTemplatePresentation()}.
 * <p>
 * {@link AnActionEvent#getPlace()} is a non-unique, free form, human-readable name of a place.
 * Some standard platform place names are listed in {@link ActionPlaces}.
 * <pre>
 * public void update(AnActionEvent e) {
 *   if (ActionPlaces.MAIN_MENU.equals(e.getPlace())) {
 *     e.getPresentation().setText("My Menu item name");
 *   }
 *   else if (ActionPlaces.MAIN_TOOLBAR.equals(e.getPlace())) {
 *     e.getPresentation().setText("My Toolbar item name");
 *   }
 * }
 * </pre>
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/basic-action-system.html">Actions (IntelliJ Platform Docs)</a>
 * @see AnActionEvent
 * @see ActionPlaces
 * @see Presentation
 * @see DataContext
 * @see com.intellij.openapi.project.DumbAwareAction
 */
public abstract class AnAction implements PossiblyDumbAware, ActionUpdateThreadAware {
  private static final Logger LOG = Logger.getInstance(AnAction.class);

  public static final Key<List<AnAction>> ACTIONS_KEY = Key.create("AnAction.shortcutSet");
  public static final AnAction[] EMPTY_ARRAY = new AnAction[0];

  private Presentation myTemplatePresentation;
  private @NotNull ShortcutSet myShortcutSet = CustomShortcutSet.EMPTY;

  private boolean myIsDefaultIcon = true;
  private SmartFMap<String, Supplier<String>> myActionTextOverrides = SmartFMap.emptyMap();
  private List<Supplier<@Nls String>> mySynonyms = Collections.emptyList();

  @ApiStatus.Internal
  int myMetaFlags;

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
    Presentation presentation = getTemplatePresentation();
    presentation.setText(dynamicText);
    presentation.setDescription(Presentation.NULL_STRING);
    presentation.setIconSupplier(null);
  }

  /**
   * Creates a new action with the given text, description and icon.
   *
   * @param text        serves as a tooltip when the presentation is a button,
   *                    and the name of the menu item when the presentation is a menu item (with mnemonic)
   * @param description describes the current action,
   *                    this description will appear on the status bar when the presentation has the focus
   * @param icon        the action's icon
   */
  public AnAction(@Nullable @ActionText String text, @Nullable @ActionDescription String description, @Nullable Icon icon) {
    this(text == null ? Presentation.NULL_STRING : () -> text,
         description == null ? Presentation.NULL_STRING : () -> description, icon);
  }

  @ApiStatus.Experimental
  public AnAction(@NotNull @ActionText Supplier<String> text,
                  @Nullable @ActionDescription Supplier<String> description,
                  @Nullable Supplier<? extends @Nullable Icon> icon) {
    Presentation presentation = getTemplatePresentation();
    presentation.setText(text);
    if (description != null) {
      presentation.setDescription(description);
    }
    presentation.setIconSupplier(icon);
  }

  @ApiStatus.Experimental
  public AnAction(@NotNull @ActionText Supplier<String> text, @NotNull @ActionDescription Supplier<String> description) {
    Presentation presentation = getTemplatePresentation();
    presentation.setText(text);
    presentation.setDescription(description);
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
    Presentation presentation = getTemplatePresentation();
    presentation.setText(dynamicText);
    if (icon != null) {
      presentation.setIcon(icon);
    }
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
    if (icon != null) {
      presentation.setIcon(icon);
    }
  }

  @Override
  public boolean isDumbAware() {
    if (PossiblyDumbAware.super.isDumbAware()) {
      return true;
    }
    return ActionClassMetaData.isDefaultUpdate(this);
  }

  /**
   * Specifies the thread and the way {@link AnAction#update(AnActionEvent)},
   * {@link ActionGroup#getChildren(AnActionEvent)} or other update-like methods shall be called.
   * <p>
   * The preferred value is {@link ActionUpdateThread#BGT}.
   * <p>
   * The default value is {@link ActionUpdateThread#EDT}.
   *
   * @see ActionUpdateThread
   */
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    if (this instanceof UpdateInBackground && ((UpdateInBackground)this).isUpdateInBackground()) {
      return ActionUpdateThread.BGT;
    }
    if (ActionClassMetaData.isDefaultUpdate(this)) {
      return ActionUpdateThread.BGT;
    }
    return ActionUpdateThread.EDT;
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
    return getTemplatePresentation().isEnabledInModalContext();
  }

  protected final void setEnabledInModalContext(boolean enabledInModalContext) {
    getTemplatePresentation().setEnabledInModalContext(enabledInModalContext);
  }

  /**
   * Return {@code true} if the action has to display its text along with the icon when placed in the toolbar.
   * <p>
   * @deprecated Use {@link com.intellij.openapi.actionSystem.ex.ActionUtil#SHOW_TEXT_IN_TOOLBAR} presentation property instead.
   */
  @Deprecated(forRemoval = true)
  public boolean displayTextInToolbar() {
    return false;
  }

  /**
   * Return {@code true} if the action displays text in a smaller font (same as toolbar combobox font) when placed in the toolbar.
   * <p>
   * @deprecated Use {@link com.intellij.openapi.actionSystem.ex.ActionUtil#USE_SMALL_FONT_IN_TOOLBAR} presentation property instead.
   */
  @Deprecated(forRemoval = true)
  public boolean useSmallerFontForTextInToolbar() {
    return false;
  }

  /**
   * Updates the presentation of the action to show a menu, a popup item, a toolbar button,
   * and when the action is invoked via a shortcut.
   * The default implementation does nothing.
   * The Platform tries its best to invoke this method and act upon the updated presentation flags
   * a little before the action is called.
   * <p>
   * Override this method to dynamically change the action's state or presentation depending on the context.
   * For example, when your action state depends on the selection,
   * you can check for the selection and change the state accordingly.
   * <p>
   * This method can be called frequently.
   * It shall be fast.
   * It must not change UI or any state, except populating some caches.
   * <p>
   * {@link #getActionUpdateThread()} controls whether the method is called on EDT or BGT.
   * BGT actions can rely on slow PSI, VFS, etc. but that can lead to slow menus and popups.
   * To speed them up, you can move slow checks to {@link #actionPerformed(AnActionEvent)} method
   * and notify the user that the action cannot be executed when it is so.
   * <p>
   * If the action is added to a toolbar, its {@code update} method can be called twice a second,
   * but only if there was any user activity or a focus transfer.
   * If your action's availability is independent of these events,
   * call {@code ActivityTracker.getInstance().inc()}
   * to notify the action subsystem to update all toolbar actions
   * when your subsystem's determines that its actions' visibility might be affected.
   *
   * @see #getActionUpdateThread()
   */
  @ApiStatus.OverrideOnly
  public void update(@NotNull AnActionEvent e) {
  }

  /**
   * <b>Deprecated and unused</b>
   * The method is a part of a dropped contract.
   * Drop it ASAP and move all the required code to {@link #actionPerformed(AnActionEvent)}.
   *
   * @deprecated Move any code to {@link #actionPerformed(AnActionEvent)}
   */
  @Deprecated(forRemoval = true)
  @ApiStatus.OverrideOnly
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
    }
    return presentation;
  }

  @NotNull
  Presentation createTemplatePresentation() {
    Presentation presentation = Presentation.newTemplatePresentation();
    if (displayTextInToolbar()) {
      presentation.putClientProperty("SHOW_TEXT_IN_TOOLBAR", true);
      if (useSmallerFontForTextInToolbar()) {
        presentation.putClientProperty("USE_SMALL_FONT_IN_TOOLBAR", true);
      }
    }
    return presentation;
  }

  /**
   * A shortcut for {@code getTemplatePresentation().getText()}.
   */
  public final @ActionText String getTemplateText() {
    return getTemplatePresentation().getText();
  }

  /**
   * Performs the action logic.
   * <p>
   * It is called on the UI thread with all data in the provided {@link DataContext} instance.
   *
   * @see #beforeActionPerformedUpdate(AnActionEvent)
   */
  public abstract void actionPerformed(@NotNull AnActionEvent e);

  @ApiStatus.Internal
  public void setShortcutSet(@NotNull ShortcutSet shortcutSet) {
    if (myShortcutSet != shortcutSet &&
        myShortcutSet != CustomShortcutSet.EMPTY &&
        LoadingState.PROJECT_OPENED.isOccurred()) {
      ActionManager actionManager = ApplicationManager.getApplication().getServiceIfCreated(ActionManager.class);
      if (actionManager != null && actionManager.getId(this) != null) {
        LOG.warn(PluginException.createByClass(
          "ShortcutSet of global AnActions should not be changed outside of KeymapManager.\n" +
          "This is likely not what you wanted to do. Consider setting shortcut in keymap defaults, inheriting from other action " +
          "using `use-shortcut-of` or wrapping with EmptyAction.wrap(). Action: " + this,
          null,
          getClass())
        );
      }
    }
    myShortcutSet = shortcutSet;
  }

  /**
   * Sets the flag indicating whether the action has an internal or a user-customized icon.
   *
   * @param isDefaultIconSet {@code true} if the icon is internal, {@code false} if the user customizes the icon
   * <p>
   * TODO Move to template presentation client properties and drop the method.
   */
  @ApiStatus.Internal
  public void setDefaultIcon(boolean isDefaultIconSet) {
    myIsDefaultIcon = isDefaultIconSet;
  }

  /**
   * @return {@code true} if the icon is internal, {@code false} if the user customizes the icon.
   * <p>
   * TODO Move to template presentation client properties and drop the method.
   */
  @ApiStatus.Internal
  public boolean isDefaultIcon() {
    return myIsDefaultIcon;
  }

  /**
   * Enables automatic detection of injected fragments in the editor.
   * Values that are passed to the action in its {@code DataContext}, like EDITOR or PSI_FILE,
   * will refer to an injected fragment if the caret is currently positioned on it.
   */
  public void setInjectedContext(boolean worksInInjected) {
    getTemplatePresentation().setPreferInjectedPsi(worksInInjected);
  }

  public boolean isInInjectedContext() {
    return getTemplatePresentation().isPreferInjectedPsi();
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
      LOG.error(PluginException.createByClass(
        "Missing override-text for action id: " + id + ", use-text-of-place: " + fromPlace,
        null,
        getClass()));
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

  public static @Nullable Project getEventProject(@Nullable AnActionEvent e) {
    return e == null ? null : e.getData(CommonDataKeys.PROJECT);
  }

  @Override
  public @NonNls String toString() {
    Presentation p = getTemplatePresentation();
    return p.getText() + " (" + p.getDescription() + ")";
  }
}
