// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PlaceProvider;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Container for the information necessary to execute or update an {@link AnAction}.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/action-system.html">Action System (IntelliJ Platform Docs)</a>
 * @see AnAction#actionPerformed(AnActionEvent)
 * @see AnAction#update(AnActionEvent)
 */
public class AnActionEvent implements PlaceProvider {

  private final @Nullable InputEvent myInputEvent;
  private final @NotNull ActionManager myActionManager;
  private final @NotNull DataContext myDataContext;
  private final @NotNull @NonNls String myPlace;
  private final @NotNull ActionUiKind myUiKind;
  private final @NotNull Presentation myPresentation;
  @JdkConstants.InputEventMask
  private final int myModifiers;

  private @NotNull UpdateSession myUpdateSession = UpdateSession.EMPTY;

  /** @deprecated Use {@link #createEvent(DataContext, Presentation, String, ActionUiKind, InputEvent)} or
   * {@link #AnActionEvent(DataContext, Presentation, String, ActionUiKind, InputEvent, int, ActionManager)} instead. */
  @Deprecated(forRemoval = true)
  public AnActionEvent(@Nullable InputEvent inputEvent,
                       @NotNull DataContext dataContext,
                       @NotNull @NonNls String place,
                       @NotNull Presentation presentation,
                       @NotNull ActionManager actionManager,
                       @JdkConstants.InputEventMask int modifiers) {
    this(dataContext, presentation, place, ActionUiKind.NONE, inputEvent, modifiers, actionManager);
  }

  /** @deprecated Use {@link #AnActionEvent(DataContext, Presentation, String, ActionUiKind, InputEvent, int, ActionManager)} instead. */
  @Deprecated(forRemoval = true)
  public AnActionEvent(@Nullable InputEvent inputEvent,
                       @NotNull DataContext dataContext,
                       @NotNull @NonNls String place,
                       @NotNull Presentation presentation,
                       @NotNull ActionManager actionManager,
                       @JdkConstants.InputEventMask int modifiers,
                       boolean isContextMenuAction,
                       boolean isActionToolbar) {
    this(dataContext, presentation, place,
         isContextMenuAction ? ActionUiKind.POPUP :
         isActionToolbar ? ActionUiKind.TOOLBAR :
         ActionUiKind.NONE,
         inputEvent, modifiers, actionManager);
  }

  public AnActionEvent(@NotNull DataContext dataContext,
                       @NotNull Presentation presentation,
                       @NotNull @NonNls String place,
                       @NotNull ActionUiKind uiKind,
                       @Nullable InputEvent inputEvent,
                       @JdkConstants.InputEventMask int modifiers,
                       @NotNull ActionManager actionManager) {
    presentation.assertNotTemplatePresentation();
    myInputEvent = inputEvent;
    myActionManager = actionManager;
    myDataContext = dataContext;
    myPlace = place;
    myPresentation = presentation;
    myModifiers = modifiers;
    myUiKind = uiKind;
  }

  public final @NotNull AnActionEvent withDataContext(@NotNull DataContext dataContext) {
    if (myDataContext == dataContext) return this;
    AnActionEvent event = new AnActionEvent(dataContext, myPresentation, myPlace, myUiKind, myInputEvent,
                                            myModifiers, myActionManager);
    event.setUpdateSession(myUpdateSession);
    return event;
  }

  public static @NotNull AnActionEvent createEvent(@NotNull AnAction action,
                                                   @NotNull DataContext dataContext,
                                                   @Nullable Presentation presentation,
                                                   @NotNull String place,
                                                   @NotNull ActionUiKind uiKind,
                                                   @Nullable InputEvent event) {
    Presentation p = presentation == null ? action.getTemplatePresentation().clone() : presentation;
    AnActionEvent result = createEvent(dataContext, p, place, uiKind, event);
    result.setInjectedContext(action.isInInjectedContext());
    return result;
  }

  public static @NotNull AnActionEvent createEvent(@NotNull DataContext dataContext,
                                                   @Nullable Presentation presentation,
                                                   @NotNull String place,
                                                   @NotNull ActionUiKind uiKind,
                                                   @Nullable InputEvent event) {
    //noinspection MagicConstant
    return new AnActionEvent(dataContext, presentation == null ? new Presentation() : presentation,
                             place, uiKind, event, event == null ? 0 : event.getModifiers(),
                             ActionManager.getInstance());
  }

  /** @deprecated use {@link #createEvent(DataContext, Presentation, String, ActionUiKind, InputEvent)} */
  @Deprecated(forRemoval = true)
  public static @NotNull AnActionEvent createFromInputEvent(@NotNull AnAction action, @Nullable InputEvent event, @NotNull String place) {
    DataContext context = event == null ? DataManager.getInstance().getDataContext() :
                          DataManager.getInstance().getDataContext(event.getComponent());
    return createFromAnAction(action, event, place, context);
  }

  /** @deprecated use {@link #createEvent(AnAction, DataContext, Presentation, String, ActionUiKind, InputEvent)} */
  @Deprecated(forRemoval = true)
  public static @NotNull AnActionEvent createFromAnAction(@NotNull AnAction action,
                                                          @Nullable InputEvent event,
                                                          @NotNull String place,
                                                          @NotNull DataContext dataContext) {
    return createEvent(action, dataContext, null, place, ActionUiKind.NONE, event);
  }

  /** @deprecated use {@link #createEvent(DataContext, Presentation, String, ActionUiKind, InputEvent)} */
  @Deprecated(forRemoval = true)
  public static @NotNull AnActionEvent createFromDataContext(@NotNull String place,
                                                             @Nullable Presentation presentation,
                                                             @NotNull DataContext dataContext) {
    return createEvent(dataContext, presentation, place, ActionUiKind.NONE, null);
  }


  /** @deprecated use {@link #createEvent(DataContext, Presentation, String, ActionUiKind, InputEvent)} */
  @Deprecated(forRemoval = true)
  public static @NotNull AnActionEvent createFromInputEvent(@Nullable InputEvent event,
                                                            @NotNull String place,
                                                            @Nullable Presentation presentation,
                                                            @NotNull DataContext dataContext) {
    return createEvent(dataContext, presentation, place, ActionUiKind.NONE, event);
  }

  /** @deprecated Use {@link #createEvent(DataContext, Presentation, String, ActionUiKind, InputEvent)} */
  @Deprecated(forRemoval = true)
  public static @NotNull AnActionEvent createFromInputEvent(@Nullable InputEvent event,
                                                            @NotNull String place,
                                                            @Nullable Presentation presentation,
                                                            @NotNull DataContext dataContext,
                                                            boolean isContextMenuAction,
                                                            boolean isToolbarAction) {
    ActionUiKind uiKind = isContextMenuAction ? ActionUiKind.POPUP :
                          isToolbarAction ? ActionUiKind.TOOLBAR :
                          ActionUiKind.NONE;
    return createEvent(dataContext, presentation, place, uiKind, event);
  }

  /**
   * Returns {@code InputEvent} which causes invocation of the action. It might be
   * {@link KeyEvent} or {@link MouseEvent} in the following user interactions:
   * <ul>
   * <li> Shortcut event, see {@link com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher IdeKeyEventDispatcher}
   * <li> Menu event, see {@link com.intellij.openapi.actionSystem.impl.ActionMenuItem ActionMenuItem}
   * <li> Standard button in toolbar, see {@link com.intellij.openapi.actionSystem.impl.ActionButton ActionButton}
   * </ul>
   * <p>
   * In other cases the value is null, for example:
   * <ul>
   * <li> Search everywhere and find actions
   * <li> Customized toolbar components, see {@link CustomComponentAction}
   * <li> Actions from notifications
   * <li> Actions that invoked programmatically
   * <li> Macros replay
   * <li> Tests
   * </ul>
   */
  public final @Nullable InputEvent getInputEvent() {
    return myInputEvent;
  }

  /**
   * @return Project from the context of this event.
   */
  public final @Nullable Project getProject() {
    return getData(CommonDataKeys.PROJECT);
  }

  public static @NotNull DataContext getInjectedDataContext(@NotNull DataContext dataContext) {
    if (dataContext == DataContext.EMPTY_CONTEXT) return dataContext;
    if (dataContext instanceof InjectedDataContextSupplier o) {
      return o.getInjectedDataContext();
    }
    return new InjectedDataContext(dataContext);
  }

  /**
   * Returns the context which allows to retrieve information about the state of the IDE related to
   * the action invocation (active editor, selection and so on).
   *
   * @return the data context instance.
   */
  public final @NotNull DataContext getDataContext() {
    return myPresentation.isPreferInjectedPsi() ? getInjectedDataContext(myDataContext) : myDataContext;
  }

  public final @Nullable <T> T getData(@NotNull DataKey<T> key) {
    return getDataContext().getData(key);
  }

  /**
   * Returns not null data by a data key. This method assumes that data has been checked for {@code null} before.
   *
   * @deprecated See the {@link AnAction#beforeActionPerformedUpdate(AnActionEvent)} javadoc.
   * @see #getData(DataKey)
   */
  @Deprecated(forRemoval = true)
  public final @NotNull <T> T getRequiredData(@NotNull DataKey<T> key) {
    T data = getData(key);
    if (data == null) throw new AssertionError(key.getName() + " is missing");
    return data;
  }

  /**
   * Returns some user defined string intended for stats, logging and debugging.
   *
   * @see com.intellij.openapi.actionSystem.ActionPlaces
   */
  @Override
  public final @NotNull @NonNls String getPlace() {
    return myPlace;
  }

  /**
   * Returns the kind of UI for which the event is created - a toolbar, a menu, a popup.
   */
  public final @NotNull ActionUiKind getUiKind() {
    return myUiKind;
  }

  /**
   * @see #getUiKind()
   * @see ActionUiKind#TOOLBAR
   */
  public final boolean isFromActionToolbar() {
    return myUiKind instanceof ActionUiKind.Toolbar;
  }

  /**
   * @see #getUiKind()
   * @see ActionUiKind#POPUP
   */
  public final boolean isFromContextMenu() {
    return myUiKind instanceof ActionUiKind.Popup o && !o.isMainMenu() && !o.isSearchPopup();
  }

  /**
   * @see #getUiKind()
   * @see ActionUiKind#POPUP
   */
  public final boolean isFromMainMenu() {
    return myUiKind instanceof ActionUiKind.Popup o && o.isMainMenu();
  }

  /**
   * @see #getUiKind()
   * @see ActionUiKind#POPUP
   */
  public final boolean isFromSearchPopup() {
    return myUiKind instanceof ActionUiKind.Popup o && o.isSearchPopup();
  }

  /**
   * Returns the presentation which represents the action in the place from where it is invoked
   * or updated.
   *
   * @return the presentation instance.
   */
  public final @NotNull Presentation getPresentation() {
    return myPresentation;
  }

  /**
   * Returns the modifier keys held down during this action event.
   *
   * @return the modifier keys.
   */
  @JdkConstants.InputEventMask
  public final int getModifiers() {
    return myModifiers;
  }

  public final @NotNull ActionManager getActionManager() {
    return myActionManager;
  }

  public final void setInjectedContext(boolean worksInInjected) {
    myPresentation.setPreferInjectedPsi(worksInInjected);
  }

  public final boolean isInInjectedContext() {
    return myPresentation.isPreferInjectedPsi();
  }

  public void accept(@NotNull AnActionEventVisitor visitor) {
    visitor.visitEvent(this);
  }

  public final @NotNull UpdateSession getUpdateSession() {
    return myUpdateSession;
  }

  public final void setUpdateSession(@NotNull UpdateSession updateSession) {
    myUpdateSession = updateSession;
  }

  @ApiStatus.Internal
  public interface InjectedDataContextSupplier {
    @NotNull DataContext getInjectedDataContext();
  }

  @Deprecated(forRemoval = true)
  private static class InjectedDataContext extends CustomizedDataContext {
    InjectedDataContext(@NotNull DataContext context) {
      super(context, true);
      Logger.getInstance(InjectedDataContext.class).error("Unsupported " + context.getClass().getName());
    }

    @Override
    public @Nullable Object getRawCustomData(@NotNull String dataId) {
      String injectedId = InjectedDataKeys.injectedId(dataId);
      return injectedId != null ? getParent().getData(injectedId) : null;
    }
  }
}
