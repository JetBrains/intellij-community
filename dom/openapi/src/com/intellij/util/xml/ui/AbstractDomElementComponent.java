package com.intellij.util.xml.ui;

import com.intellij.openapi.Disposable;
import com.intellij.util.xml.DomElement;
import com.intellij.util.ui.UIUtil;

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

  protected static void setEnabled(Component component, boolean enabled) {
    UIUtil.setEnabled(component, enabled, true);
  }
}
