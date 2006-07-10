package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;
import com.intellij.openapi.Disposable;

import java.util.List;
import java.util.ArrayList;
import java.awt.*;


/**
 * User: Sergey.Vasiliev
 * Date: Nov 18, 2005
 */
public abstract class AbstractDomElementComponent<T extends DomElement> extends CompositeCommittable implements CommittablePanel, Disposable {
  protected T myDomElement;
  protected List<Committable> myRelatedCommitable = new ArrayList<Committable>();

  protected AbstractDomElementComponent(final T domElement) {
    myDomElement = domElement;
  }

  public T getDomElement() {
    return myDomElement;
  }

  protected static void setEnabled(Component component, boolean enabled) {
    component.setEnabled(enabled);
    if (component instanceof Container) {
      for (Component child : ((Container)component).getComponents()) {
        setEnabled(child, enabled);
      }
    }
  }


  public List<Committable> getRelatedCommitable() {
    return myRelatedCommitable;
  }

  public void addRelatedCommitable(final Committable relatedCommitable) {
    myRelatedCommitable.add(relatedCommitable);
  }

  public void removeRelatedCommitable(final Committable relatedCommitable) {
    myRelatedCommitable.remove(relatedCommitable);
  }


  public void commit() {
    super.commit();
    //for (Committable committable : myRelatedCommitable) {
    //  committable.commit();
    //}
  }

  public void reset() {
    super.reset();
    for (Committable committable : myRelatedCommitable) {
      committable.reset();
    }
  }
}
