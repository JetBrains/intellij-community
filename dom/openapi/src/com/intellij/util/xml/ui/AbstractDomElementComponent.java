package com.intellij.util.xml.ui;

import com.intellij.openapi.Disposable;
import com.intellij.util.xml.DomElement;

import java.awt.*;


/**
 * User: Sergey.Vasiliev
 * Date: Nov 18, 2005
 */
public abstract class AbstractDomElementComponent<T extends DomElement> extends CompositeCommittable
  implements CommittablePanel, Disposable {
  protected T myDomElement;

  protected AbstractDomElementComponent(final T domElement) {
    myDomElement = domElement;
  }

  public T getDomElement() {
    return myDomElement;
  }

  public void commit() {
    super.commit();
  }

  public void reset() {
    super.reset();
  }

  protected static void setEnabled(Component component, boolean enabled) {
    component.setEnabled(enabled);
    if (component instanceof Container) {
      for (Component child : ((Container)component).getComponents()) {
        setEnabled(child, enabled);
      }
    }
  }
}
