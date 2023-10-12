// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
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
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/basic-action-system.html">Actions (IntelliJ Platform Docs)</a>
 * @see AnAction#actionPerformed(AnActionEvent)
 * @see AnAction#update(AnActionEvent)
 */
public class AnActionEvent implements PlaceProvider {

  private final @Nullable InputEvent myInputEvent;
  private final @NotNull ActionManager myActionManager;
  private final @NotNull DataContext myDataContext;
  private final @NotNull @NonNls String myPlace;
  private final @NotNull Presentation myPresentation;
  @JdkConstants.InputEventMask
  private final int myModifiers;
  private final boolean myIsContextMenuAction;
  private final boolean myIsActionToolbar;

  private boolean myWorksInInjected;
  private @NotNull UpdateSession myUpdateSession = UpdateSession.EMPTY;

  /**
   * @throws IllegalArgumentException if {@code dataContext} is {@code null} or
   *                                  {@code place} is {@code null} or {@code presentation} is {@code null}
   * @see ActionManager#getInstance()
   */
  public AnActionEvent(@Nullable InputEvent inputEvent,
                       @NotNull DataContext dataContext,
                       @NotNull @NonNls String place,
                       @NotNull Presentation presentation,
                       @NotNull ActionManager actionManager,
                       @JdkConstants.InputEventMask int modifiers) {
    this(inputEvent, dataContext, place, presentation, actionManager, modifiers, false, false);
  }

  /**
   * @throws IllegalArgumentException if {@code dataContext} is {@code null} or
   *                                  {@code place} is {@code null} or {@code presentation} is {@code null}
   * @see ActionManager#getInstance()
   */
  public AnActionEvent(@Nullable InputEvent inputEvent,
                       @NotNull DataContext dataContext,
                       @NotNull @NonNls String place,
                       @NotNull Presentation presentation,
                       @NotNull ActionManager actionManager,
                       @JdkConstants.InputEventMask int modifiers,
                       boolean isContextMenuAction,
                       boolean isActionToolbar) {
    presentation.assertNotTemplatePresentation();
    myInputEvent = inputEvent;
    myActionManager = actionManager;
    myDataContext = dataContext;
    myPlace = place;
    myPresentation = presentation;
    myModifiers = modifiers;
    myIsContextMenuAction = isContextMenuAction;
    myIsActionToolbar = isActionToolbar;
  }

  public @NotNull AnActionEvent withDataContext(@NotNull DataContext dataContext) {
    if (myDataContext == dataContext) return this;
    AnActionEvent event = new AnActionEvent(myInputEvent, dataContext, myPlace, myPresentation,
                                            myActionManager, myModifiers, myIsContextMenuAction, myIsActionToolbar);
    event.setInjectedContext(myWorksInInjected);
    event.setUpdateSession(myUpdateSession);
    return event;
  }

  /**
   * @deprecated use {@link #createFromInputEvent(InputEvent, String, Presentation, DataContext, boolean, boolean)}
   */
  @Deprecated(forRemoval = true)
  public static @NotNull AnActionEvent createFromInputEvent(@NotNull AnAction action, @Nullable InputEvent event, @NotNull String place) {
    DataContext context =
      event == null ? DataManager.getInstance().getDataContext() : DataManager.getInstance().getDataContext(event.getComponent());
    return createFromAnAction(action, event, place, context);
  }

  public static @NotNull AnActionEvent createFromAnAction(@NotNull AnAction action,
                                                          @Nullable InputEvent event,
                                                          @NotNull String place,
                                                          @NotNull DataContext dataContext) {
    int modifiers = event == null ? 0 : event.getModifiers();
    Presentation presentation = action.getTemplatePresentation().clone();
    AnActionEvent anActionEvent = new AnActionEvent(event, dataContext, place, presentation, ActionManager.getInstance(), modifiers);
    anActionEvent.setInjectedContext(action.isInInjectedContext());
    return anActionEvent;
  }

  public static @NotNull AnActionEvent createFromDataContext(@NotNull String place,
                                                             @Nullable Presentation presentation,
                                                             @NotNull DataContext dataContext) {
    return new AnActionEvent(null, dataContext, place, presentation == null ? new Presentation() : presentation,
                             ActionManager.getInstance(), 0);
  }


  public static @NotNull AnActionEvent createFromInputEvent(@Nullable InputEvent event,
                                                            @NotNull String place,
                                                            @Nullable Presentation presentation,
                                                            @NotNull DataContext dataContext) {
    return createFromInputEvent(event, place, presentation, dataContext, false, false);
  }

  public static @NotNull AnActionEvent createFromInputEvent(@Nullable InputEvent event,
                                                            @NotNull String place,
                                                            @Nullable Presentation presentation,
                                                            @NotNull DataContext dataContext,
                                                            boolean isContextMenuAction,
                                                            boolean isToolbarAction) {
    return new AnActionEvent(event, dataContext, place, presentation == null ? new Presentation() : presentation,
                             ActionManager.getInstance(),
                             event == null ? 0 : event.getModifiers(), isContextMenuAction, isToolbarAction);
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
  public @Nullable InputEvent getInputEvent() {
    return myInputEvent;
  }

  /**
   * @return Project from the context of this event.
   */
  public @Nullable Project getProject() {
    return getData(CommonDataKeys.PROJECT);
  }

  public static @NotNull DataContext getInjectedDataContext(@NotNull DataContext dataContext) {
    if (dataContext instanceof InjectedDataContextSupplier) {
      return ((InjectedDataContextSupplier)dataContext).getInjectedDataContext();
    }
    return dataContext instanceof InjectedDataContext ? dataContext : new InjectedDataContext(dataContext);
  }

  /**
   * Returns the context which allows to retrieve information about the state of the IDE related to
   * the action invocation (active editor, selection and so on).
   *
   * @return the data context instance.
   */
  public @NotNull DataContext getDataContext() {
    return myWorksInInjected ? getInjectedDataContext(myDataContext) : myDataContext;
  }

  /**
   * @see #getRequiredData(DataKey)
   */
  public @Nullable <T> T getData(@NotNull DataKey<T> key) {
    return getDataContext().getData(key);
  }

  /**
   * Returns not null data by a data key. This method assumes that data has been checked for {@code null} in {@code AnAction#update} method.
   * <br/><br/>
   * Example of proper usage:
   *
   * <pre>
   *
   * public class MyAction extends AnAction {
   *   public void update(AnActionEvent e) {
   *     // perform action if and only if EDITOR != null
   *     boolean enabled = e.getData(CommonDataKeys.EDITOR) != null;
   *     e.getPresentation().setEnabled(enabled);
   *   }
   *
   *   public void actionPerformed(AnActionEvent e) {
   *     // if we're here then EDITOR != null
   *     Document doc = e.getRequiredData(CommonDataKeys.EDITOR).getDocument();
   *     doSomething(doc);
   *   }
   * }
   *
   * </pre>
   * @see #getData(DataKey)
   */
  public @NotNull <T> T getRequiredData(@NotNull DataKey<T> key) {
    T data = getData(key);
    assert data != null : key.getName() + " is missing";
    return data;
  }

  /**
   * Returns the identifier of the place in the IDE user interface from where the action is invoked
   * or updated.
   *
   * @return the place identifier
   * @see com.intellij.openapi.actionSystem.ActionPlaces
   */
  @Override
  public @NotNull @NonNls String getPlace() {
    return myPlace;
  }

  public boolean isFromActionToolbar() {
    return myIsActionToolbar;
  }

  /**
   * @deprecated This method returns {@code true} for both main menu and context menu invocations. Use {@link ActionPlaces#isPopupPlace(String)}
   * instead to get results only from context menus.
   */
  @Deprecated(forRemoval = true)
  public boolean isFromContextMenu() {
    return myIsContextMenuAction;
  }

  /**
   * Returns the presentation which represents the action in the place from where it is invoked
   * or updated.
   *
   * @return the presentation instance.
   */
  public @NotNull Presentation getPresentation() {
    return myPresentation;
  }

  /**
   * Returns the modifier keys held down during this action event.
   *
   * @return the modifier keys.
   */
  @JdkConstants.InputEventMask
  public int getModifiers() {
    return myModifiers;
  }

  public @NotNull ActionManager getActionManager() {
    return myActionManager;
  }

  public void setInjectedContext(boolean worksInInjected) {
    myWorksInInjected = worksInInjected;
  }

  public boolean isInInjectedContext() {
    return myWorksInInjected;
  }

  public void accept(@NotNull AnActionEventVisitor visitor) {
    visitor.visitEvent(this);
  }

  public @NotNull UpdateSession getUpdateSession() {
    return myUpdateSession;
  }

  public void setUpdateSession(@NotNull UpdateSession updateSession) {
    myUpdateSession = updateSession;
  }

  @ApiStatus.Internal
  public interface InjectedDataContextSupplier {
    @NotNull DataContext getInjectedDataContext();
  }

  private static class InjectedDataContext extends DataContextWrapper {
    InjectedDataContext(@NotNull DataContext context) { super(context); }

    @Override
    public @Nullable Object getRawCustomData(@NotNull String dataId) {
      String injectedId = InjectedDataKeys.injectedId(dataId);
      return injectedId != null ? getParent().getData(injectedId) : null;
    }
  }
}
