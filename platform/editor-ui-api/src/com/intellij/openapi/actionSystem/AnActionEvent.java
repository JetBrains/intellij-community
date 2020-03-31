// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.ide.DataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.PlaceProvider;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Container for the information necessary to execute or update an {@link AnAction}.
 *
 * @see AnAction#actionPerformed(AnActionEvent)
 * @see AnAction#update(AnActionEvent)
 */
public class AnActionEvent implements PlaceProvider<String> {
  private final InputEvent myInputEvent;
  @NotNull private final ActionManager myActionManager;
  @NotNull private final DataContext myDataContext;
  @NotNull private final String myPlace;
  @NotNull private final Presentation myPresentation;
  @JdkConstants.InputEventMask private final int myModifiers;
  private boolean myWorksInInjected;
  @NonNls private static final String ourInjectedPrefix = "$injected$.";
  private static final Map<String, String> ourInjectedIds = new HashMap<>();
  private final boolean myIsContextMenuAction;
  private final boolean myIsActionToolbar;

  /**
   * @throws IllegalArgumentException if {@code dataContext} is {@code null} or
   * {@code place} is {@code null} or {@code presentation} is {@code null}
   *
   * @see ActionManager#getInstance()
   */
  public AnActionEvent(InputEvent inputEvent,
                       @NotNull DataContext dataContext,
                       @NotNull @NonNls String place,
                       @NotNull Presentation presentation,
                       @NotNull ActionManager actionManager,
                       @JdkConstants.InputEventMask int modifiers) {
    this(inputEvent, dataContext, place, presentation, actionManager, modifiers, false, false);
  }

  /**
   * @throws IllegalArgumentException if {@code dataContext} is {@code null} or
   * {@code place} is {@code null} or {@code presentation} is {@code null}
   *
   * @see ActionManager#getInstance()
   */
  public AnActionEvent(InputEvent inputEvent,
                       @NotNull DataContext dataContext,
                       @NotNull @NonNls String place,
                       @NotNull Presentation presentation,
                       @NotNull ActionManager actionManager,
                       @JdkConstants.InputEventMask int modifiers,
                       boolean isContextMenuAction,
                       boolean isActionToolbar) {
    // TODO[vova,anton] make this constructor package-private. No one is allowed to create AnActionEvents
    myInputEvent = inputEvent;
    myActionManager = actionManager;
    myDataContext = dataContext;
    myPlace = place;
    myPresentation = presentation;
    myModifiers = modifiers;
    myIsContextMenuAction = isContextMenuAction;
    myIsActionToolbar = isActionToolbar;
  }


  /**
   * @deprecated use {@link #createFromInputEvent(InputEvent, String, Presentation, DataContext, boolean, boolean)}
   */
  @Deprecated
  @NotNull
  public static AnActionEvent createFromInputEvent(@NotNull AnAction action, @Nullable InputEvent event, @NotNull String place) {
    DataContext context = event == null ? DataManager.getInstance().getDataContext() : DataManager.getInstance().getDataContext(event.getComponent());
    return createFromAnAction(action, event, place, context);
  }

  @NotNull
  public static AnActionEvent createFromAnAction(@NotNull AnAction action,
                                                 @Nullable InputEvent event,
                                                 @NotNull String place,
                                                 @NotNull DataContext dataContext) {
    int modifiers = event == null ? 0 : event.getModifiers();
    Presentation presentation = action.getTemplatePresentation().clone();
    AnActionEvent anActionEvent = new AnActionEvent(event, dataContext, place, presentation, ActionManager.getInstance(), modifiers);
    anActionEvent.setInjectedContext(action.isInInjectedContext());
    return anActionEvent;
  }

  @NotNull
  public static AnActionEvent createFromDataContext(@NotNull String place,
                                                    @Nullable Presentation presentation,
                                                    @NotNull DataContext dataContext) {
    return new AnActionEvent(null, dataContext, place, presentation == null ? new Presentation() : presentation, ActionManager.getInstance(), 0);
  }


  @NotNull
  public static AnActionEvent createFromInputEvent(@Nullable InputEvent event,
                                                   @NotNull String place,
                                                   @Nullable Presentation presentation,
                                                   @NotNull DataContext dataContext) {
    return createFromInputEvent(event, place, presentation, dataContext, false, false);
  }

  @NotNull
  public static AnActionEvent createFromInputEvent(@Nullable InputEvent event,
                                                   @NotNull String place,
                                                   @Nullable Presentation presentation,
                                                   @NotNull DataContext dataContext,
                                                   boolean isContextMenuAction,
                                                   boolean isToolbarAction) {
    return new AnActionEvent(event, dataContext, place, presentation == null ? new Presentation() : presentation, ActionManager.getInstance(),
                             event == null ? 0 : event.getModifiers(), isContextMenuAction, isToolbarAction);
  }

  /**
   * Returns the {@code InputEvent} which causes invocation of the action. It might be
   * {@code KeyEvent}, {@code MouseEvent}.
   *
   * @return the {@code InputEvent} instance.
   */
  public InputEvent getInputEvent() {
    return myInputEvent;
  }

  /**
   * @return Project from the context of this event.
   */
  @Nullable
  public Project getProject() {
    return getData(CommonDataKeys.PROJECT);
  }

  @NonNls
  @NotNull
  public static String injectedId(@NotNull String dataId) {
    synchronized(ourInjectedIds) {
      return ourInjectedIds.computeIfAbsent(dataId, i -> ourInjectedPrefix + i);
    }
  }

  @NonNls
  @NotNull
  public static String uninjectedId(@NotNull String dataId) {
    return StringUtil.trimStart(dataId, ourInjectedPrefix);
  }

  @NotNull
  public static DataContext getInjectedDataContext(@NotNull DataContext context) {
    return new DataContextWrapper(context) {
      @Nullable
      @Override
      public Object getData(@NotNull @NonNls String dataId) {
        Object injected = super.getData(injectedId(dataId));
        if (injected != null) return injected;
        return super.getData(dataId);
      }
    };
  }

  /**
   * Returns the context which allows to retrieve information about the state of IDE related to
   * the action invocation (active editor, selection and so on).
   *
   * @return the data context instance.
   */
  @NotNull
  public DataContext getDataContext() {
    return myWorksInInjected ? getInjectedDataContext(myDataContext) : myDataContext;
  }

  @Nullable
  public <T> T getData(@NotNull DataKey<T> key) {
    return getDataContext().getData(key);
  }

  /**
   * Returns not null data by a data key. This method assumes that data has been checked for {@code null} in {@code AnAction#update} method.
   *<br/><br/>
   * Example of proper usage:
   *
   * <pre>
   *
   * public class MyAction extends AnAction {
   *   public void update(AnActionEvent e) {
   *     // perform action if and only if EDITOR != null
   *     boolean enabled = e.getData(CommonDataKeys.EDITOR) != null;
   *     e.getPresentation.setEnabled(enabled);
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
   */
  @NotNull
  public <T> T getRequiredData(@NotNull DataKey<T> key) {
    T data = getData(key);
    assert data != null;
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
  @NotNull
  public String getPlace() {
    return myPlace;
  }

  public boolean isFromActionToolbar() {
    return myIsActionToolbar;
  }

  /**
   * @deprecated This method returns {@code true} for both main menu and context menu invocations. Use {@link ActionPlaces#isPopupPlace(String)}
   * instead to get results only from context menus.
   */
  @Deprecated
  public boolean isFromContextMenu() {
    return myIsContextMenuAction;
  }

  /**
   * Returns the presentation which represents the action in the place from where it is invoked
   * or updated.
   *
   * @return the presentation instance.
   */
  @NotNull
  public Presentation getPresentation() {
    return myPresentation;
  }

  /**
   * Returns the modifier keys held down during this action event.
   * @return the modifier keys.
   */
  @JdkConstants.InputEventMask
  public int getModifiers() {
    return myModifiers;
  }

  @NotNull
  public ActionManager getActionManager() {
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
}
