/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

public class SelectInManager implements JDOMExternalizable, ProjectComponent {
  private ArrayList<SelectInTarget> myTargets = new ArrayList<SelectInTarget>();
  private ArrayList myOrder = new ArrayList();

  private SelectInManager() {
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public void addTarget(SelectInTarget target) {
    myTargets.add(target);
  }

  public void removeTarget(SelectInTarget target) {
    myTargets.remove(target);
  }

  public void moveToTop(SelectInTarget target) {
    String targetName = target.toString();
    if (myOrder.contains(targetName)) {
      myOrder.remove(targetName);
    }
    myOrder.add(0, targetName);
  }

  public SelectInTarget[] getTargets() {
    SelectInTarget[] targets = myTargets.toArray(new SelectInTarget[myTargets.size()]);
    for(int i = 0; i < targets.length; i++){
      String name = targets[i].toString();
      if (!myOrder.contains(name)) {
        myOrder.add(name);
      }
    }

    Arrays.sort(targets, new Comparator() {
      public int compare(Object o1, Object o2) {
        int index1 = myOrder.indexOf(o1.toString());
        int index2 = myOrder.indexOf(o2.toString());
        return index1 - index2;
      }
    });

    return targets;
  }

  public static SelectInManager getInstance(Project project) {
    return project.getComponent(SelectInManager.class);
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    myOrder.clear();
    for (Iterator iterator = parentNode.getChildren("target").iterator(); iterator.hasNext();) {
      Element element = (Element)iterator.next();
      myOrder.add(element.getAttributeValue("name"));
    }
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    for(Iterator iterator = myOrder.iterator(); iterator.hasNext();){
      String targetName = (String)iterator.next();
      Element e = new Element("target");
      e.setAttribute("name", targetName);
      parentNode.addContent(e);
    }
  }

  public String getComponentName() {
    return "SelectInManager";
  }

}
