/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 15, 2001
 * Time: 5:14:35 PM
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.InspectionsBundle;

import java.util.ArrayList;
import java.util.List;

public abstract class RefEntityImpl implements RefEntity {
  private RefEntityImpl myOwner;
  private ArrayList<RefEntity> myChildren;
  private final String myName;

  protected RefEntityImpl(String name) {
    myName = name != null ? name : InspectionsBundle.message("inspection.reference.noname");
    myOwner = null;
    myChildren = null;
  }

  public String getName() {
    return myName;
  }

  public List<RefEntity> getChildren() {
    return myChildren;
  }

  public RefEntity getOwner() {
    return myOwner;
  }

  private void setOwner(RefEntityImpl owner) {
    myOwner = owner;
  }

  public void add(RefEntity child) {
    if (myChildren == null) {
      myChildren = new ArrayList<RefEntity>();
    }

    myChildren.add(child);
    ((RefEntityImpl)child).setOwner(this);
  }

  protected void removeChild(RefEntity child) {
    if (myChildren != null) {
      myChildren.remove(child);
      ((RefEntityImpl)child).setOwner(null);
    }
  }

  public String toString() {
    return getName();
  }
}
