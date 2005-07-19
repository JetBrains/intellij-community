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

import java.awt.event.InputEvent;

public final class AnActionEvent {
  private final InputEvent myInputEvent;
  private final ActionManager myActionManager;
  private final DataContext myDataContext;
  private final String myPlace;
  private final Presentation myPresentation;
  private final int myModifiers;

  /**
   * @throws java.lang.IllegalArgumentException <code>dataContext</code> is <code>null</code> or
   * <code>place</code> is <code>null</code> or <code>presentation</code> is <code>null</code>
   */
  public AnActionEvent(
    InputEvent inputEvent,
    DataContext dataContext,
    String place,
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
   * @return <code>InputEvent</code> which causes invocation of the action. It might be
   * <code>KeyEvent</code>, <code>MouseEvent</code>
   */
  public InputEvent getInputEvent(){
    return myInputEvent;
  }

  public DataContext getDataContext(){
    return myDataContext;
  }

  public String getPlace(){
    return myPlace;
  }

  public Presentation getPresentation() {
    return myPresentation;
  }

  /**
   * @return the modifier keys held down during this action event
   */
  public int getModifiers(){
    return myModifiers;
  }

  public ActionManager getActionManager() {
    return myActionManager;
  }
}
