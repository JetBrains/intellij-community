/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;

/**
 * Container for the information necessary to execute or update an {@link AnAction}.
 *
 * @see AnAction#actionPerformed(AnActionEvent)
 * @see AnAction#update(AnActionEvent)
 */

public final class AnActionEvent {
  private final InputEvent myInputEvent;
  private final ActionManager myActionManager;
  private final DataContext myDataContext;
  private final String myPlace;
  private final Presentation myPresentation;
  private final int myModifiers;
  private boolean myWorksInInjected;
  @NonNls private static final String ourInjectedId = "$injected$.";
  private static final StringBuilder ourIdBuilder = new StringBuilder(ourInjectedId);

  /**
   * @throws IllegalArgumentException <code>dataContext</code> is <code>null</code> or
   * <code>place</code> is <code>null</code> or <code>presentation</code> is <code>null</code>
   */
  public AnActionEvent(
    InputEvent inputEvent,
    DataContext dataContext,
    @NonNls String place,
    Presentation presentation,
    ActionManager actionManager,
    int modifiers
  ){
    // TODO[vova,anton] make this constructor package local. No one is allowed to create AnActionEvents
    myInputEvent = inputEvent;
    myActionManager = actionManager;
    if(dataContext==null){
      throw new IllegalArgumentException("dataContext cannot be null");
    }
    myDataContext = dataContext;
    if(place==null){
      throw new IllegalArgumentException("place cannot be null");
    }
    myPlace = place;
    if(presentation==null){
      throw new IllegalArgumentException("presentation cannot be null");
    }
    myPresentation = presentation;
    myModifiers = modifiers;
  }

  /**
   * Returns the <code>InputEvent</code> which causes invocation of the action. It might be
   * <code>KeyEvent</code>, <code>MouseEvent</code>.
   * @return the <code>InputEvent</code> instance.
   */
  public InputEvent getInputEvent() {
    return myInputEvent;
  }

  @NonNls
  public static String injectedId(String dataId) {
    synchronized(ourIdBuilder) {
      ourIdBuilder.setLength(ourInjectedId.length());
      ourIdBuilder.append(dataId);
      return ourIdBuilder.toString();
    }
  }
  @NonNls
  public static String uninjectedId(String dataId) {
    return StringUtil.trimStart(dataId, ourInjectedId);
  }
  /**
   * Returns the context which allows to retrieve information about the state of IDEA related to
   * the action invocation (active editor, selection and so on).
   *
   * @return the data context instance.
   */
  public DataContext getDataContext() {
    if (!myWorksInInjected) {
      return myDataContext;
    }
    return new DataContext() {
      @Nullable
      public Object getData(@NonNls String dataId) {
        Object injected = myDataContext.getData(injectedId(dataId));
        if (injected != null) return injected;
        return myDataContext.getData(dataId);
      }
    };
  }

  /**
   * Returns the identifier of the place in the IDEA user interface from where the action is invoked
   * or updated.
   *
   * @return the place identifier
   * @see ActionPlaces
   */
  public String getPlace() {
    return myPlace;
  }

  /**
   * Returns the presentation which represents the action in the place from where it is invoked
   * or updated.
   *
   * @return the presentation instance.
   */
  public Presentation getPresentation() {
    return myPresentation;
  }

  /**
   * Returns the modifier keys held down during this action event.
   * @return the modifier keys.
   */
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
}
