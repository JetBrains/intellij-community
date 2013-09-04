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
  private final ActionManager myActionManager;
  @NotNull private final DataContext myDataContext;
  @NotNull private final String myPlace;
  @NotNull private final Presentation myPresentation;
  @JdkConstants.InputEventMask private final int myModifiers;
  private boolean myWorksInInjected;
  @NonNls private static final String ourInjectedPrefix = "$injected$.";
  private static final Map<String, String> ourInjectedIds = new HashMap<String, String>();

  /**
   * @throws IllegalArgumentException if <code>dataContext</code> is <code>null</code> or
   * <code>place</code> is <code>null</code> or <code>presentation</code> is <code>null</code>
   */
  public AnActionEvent(InputEvent inputEvent,
                       @NotNull DataContext dataContext,
                       @NotNull @NonNls String place,
                       @NotNull Presentation presentation,
                       ActionManager actionManager,
                       @JdkConstants.InputEventMask int modifiers) {
    // TODO[vova,anton] make this constructor package local. No one is allowed to create AnActionEvents
    myInputEvent = inputEvent;
    myActionManager = actionManager;
    myDataContext = dataContext;
    myPlace = place;
    myPresentation = presentation;
    myModifiers = modifiers;
  }

  @NotNull
  public static AnActionEvent createFromInputEvent(@NotNull AnAction action, InputEvent event, @NotNull String place) {
    DataContext context = event == null ? DataManager.getInstance().getDataContext() : DataManager.getInstance().getDataContext(event.getComponent());
    int modifiers = event == null ? 0 : event.getModifiers();
    return new AnActionEvent(
      event,
      context,
      place,
      action.getTemplatePresentation(),
      ActionManager.getInstance(),
      modifiers
    );
  }

  /**
   * Returns the <code>InputEvent</code> which causes invocation of the action. It might be
   * <code>KeyEvent</code>, <code>MouseEvent</code>.
   * @return the <code>InputEvent</code> instance.
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
  public static String injectedId(String dataId) {
    synchronized(ourInjectedIds) {
      String injected = ourInjectedIds.get(dataId);
      if (injected == null) {
        injected = ourInjectedPrefix + dataId;
        ourInjectedIds.put(dataId, injected);
      }
      return injected;
    }
  }

  @NonNls
  public static String uninjectedId(@NotNull String dataId) {
    return StringUtil.trimStart(dataId, ourInjectedPrefix);
  }

  /**
   * Returns the context which allows to retrieve information about the state of IDEA related to
   * the action invocation (active editor, selection and so on).
   *
   * @return the data context instance.
   */
  @NotNull
  public DataContext getDataContext() {
    if (!myWorksInInjected) {
      return myDataContext;
    }
    return new DataContext() {
      @Override
      @Nullable
      public Object getData(@NonNls String dataId) {
        Object injected = myDataContext.getData(injectedId(dataId));
        if (injected != null) return injected;
        return myDataContext.getData(dataId);
      }
    };
  }

  @Nullable
  public <T> T getData(@NotNull DataKey<T> key) {
    return key.getData(getDataContext());
  }

  @NotNull
  public <T> T getRequiredData(@NotNull DataKey<T> key) {
    T data = getData(key);
    assert data != null;
    return data;
  }

  /**
   * Returns the identifier of the place in the IDEA user interface from where the action is invoked
   * or updated.
   *
   * @return the place identifier
   * @see ActionPlaces
   */
  @Override
  @NotNull
  public String getPlace() {
    return myPlace;
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
