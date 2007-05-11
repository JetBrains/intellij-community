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
package com.intellij.openapi.wm;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * Spies how focus goes in the component.
 * @author Vladimir Kondratyev
 */
public class FocusWatcher implements ContainerListener,FocusListener{
  private Component myTopComponent;
  /**
   * Last component that had focus.
   */
  private Component myFocusedComponent;
  /**
   * TODO[vova,anton] the name getMostRecentFocusOwner is better. The description could be copied from
   * java.awt.Window.getMostRecentFocusOwner() method.
   * This is the nearest component to the myFocusableComponent
   */
  private Component myNearestFocusableComponent;

  /**
   * @return top component on which focus watcher was installed.
   * The method always return <code>null</code> if focus watcher was installed
   * on some component hierarchy.
   */
  public Component getTopComponent() {
    return myTopComponent;
  }

  public final void componentAdded(final ContainerEvent e){
    installImpl(e.getChild());
  }

  public final void componentRemoved(final ContainerEvent e){
    Component removedChild=e.getChild();
    if(myNearestFocusableComponent!=null&&SwingUtilities.isDescendingFrom(myNearestFocusableComponent,removedChild)){
      myNearestFocusableComponent=null;
    }
    if(myFocusedComponent!=null&&SwingUtilities.isDescendingFrom(myFocusedComponent,removedChild)){
      myNearestFocusableComponent=e.getContainer();
    }
    deinstall(removedChild);
  }

  public final void deinstall(final Component component){
    if(component instanceof Container){
      Container container=(Container)component;
      int componentCount=container.getComponentCount();
      for(int i=0;i<componentCount;i++){
        deinstall(container.getComponent(i));
      }
      container.removeContainerListener(this);
    }
    component.removeFocusListener(this);
    if(myFocusedComponent==component){
      setFocusedComponentImpl(null);
    }
  }

  public final void focusGained(final FocusEvent e){
    final Component component = e.getComponent();
    if(e.isTemporary()||!component.isShowing()){
      return;
    }
    setFocusedComponentImpl(component);
    myNearestFocusableComponent=component.getParent();
  }

  public final void focusLost(final FocusEvent e){
    Component component = e.getOppositeComponent();
    if(component != null && !SwingUtilities.isDescendingFrom(component, myTopComponent)){
      focusLostImpl(e);
    }
  }

  /**
   * @return last focused component or <code>null</code>.
   */
  public final Component getFocusedComponent(){
    return myFocusedComponent;
  }

  public final Component getNearestFocusableComponent() {
    return myNearestFocusableComponent;
  }

  public final void install(@NotNull Component component){
    myTopComponent = component;
    installImpl(component);
  }
  
  private void installImpl(Component component){
    if(component instanceof Container){
      Container container=(Container)component;
      int componentCount=container.getComponentCount();
      for(int i=0;i<componentCount;i++){
        installImpl(container.getComponent(i));
      }
      container.addContainerListener(this);
    }
    if(component instanceof JMenuItem||component instanceof JMenuBar){
      return;
    }
    component.addFocusListener(this);
  }

  public void setFocusedComponentImpl(Component component){
    myFocusedComponent=component;
    focusedComponentChanged(component);
  }

  /**
   * Override this method to get notifications about focus. <code>FocusWatcher</code> invokes
   * this method each time one of the populated  component gains focus. All "temporary" focus
   * event are ignored.
   *
   * @param component currenly focused component. The component can be <code>null</code>
   * if the following case: focused component was removed from the swing hierarchy.
   */
  protected void focusedComponentChanged(Component component){}
  
  protected void focusLostImpl(final FocusEvent e){}
}