/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementNavigationProvider;

import java.awt.*;
import java.util.List;

/**
 * User: Sergey.Vasiliev
 */
public class DomUINavigationProvider extends DomElementNavigationProvider {
  public static String DOM_UI_NAVIGATION_PROVIDER_NAME = "DOM_UI_NAVIGATION_PROVIDER_NAME";

  private CompositeCommittable myComponent;

  protected DomUINavigationProvider(final CompositeCommittable component) {
    myComponent = component;
  }


  public String getProviderName() {
    return DOM_UI_NAVIGATION_PROVIDER_NAME;
  }

  public void navigate(DomElement domElement, boolean requestFocus) {
    final DomUIControl domUIControl = findDomControl(myComponent, domElement);
    if(domUIControl != null) {
      domUIControl.navigate(domElement);
    }
  }

  public boolean canNavigate(DomElement domElement) {
    return findDomControl(myComponent, domElement) != null;
  }

  public static DomElement findDomElement(final BasicDomElementComponent compositCommitable) {
    final Component component = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
    if (component != null) {
      final DomElement domElement = findDomElement(compositCommitable, component);
      if (domElement != null) {
        return domElement;
      }
    }
    return compositCommitable.getDomElement();
  }

  public static DomElement findDomElement(final CompositeCommittable compositCommitable, final Component component) {
    final List<Committable> list = compositCommitable.getChildren();
    for (Committable committable : list) {
      if (committable instanceof DomUIControl) {
        final DomUIControl uiControl = (DomUIControl)committable;
        if (uiControl.getComponent().isAncestorOf(component)) {
          return uiControl.getDomElement();
        }
      } else if (committable instanceof CompositeCommittable) {
        final DomElement element = findDomElement((CompositeCommittable)committable, component);
        if (element != null) return element;
      }
    }
    return null;
  }

  public static DomUIControl findDomControl(final CompositeCommittable compositCommitable, final DomElement domElement) {
    final List<Committable> list = compositCommitable.getChildren();
    for (Committable committable : list) {
      if (committable instanceof DomUIControl) {
          if(((DomUIControl)committable).canNavigate(domElement)) {
            return (DomUIControl)committable;
          }
      } else if (committable instanceof CompositeCommittable) {
        final DomUIControl control = findDomControl((CompositeCommittable)committable, domElement);
        if(control != null) return control;
      }
    }
    return null;
  }
}
