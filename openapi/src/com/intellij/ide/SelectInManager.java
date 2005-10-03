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
package com.intellij.ide;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.j2ee.J2EEBundle;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

public class SelectInManager implements JDOMExternalizable, ProjectComponent {
  private ArrayList<SelectInTarget> myTargets = new ArrayList<SelectInTarget>();
  private ArrayList myOrder = new ArrayList();
  @NonNls
  private static final String ELEMENT_TARGET = "target";
  @NonNls
  private static final String ATTRIBUTE_NAME = "name";
  @NonNls public static final String PROJECT = IdeBundle.message("select.in.project");
  @NonNls public static final String PACKAGES = IdeBundle.message("select.in.packages");
  @NonNls public static final String ASPECTS = IdeBundle.message("select.in.aspects");
  @NonNls public static final String COMMANDER = IdeBundle.message("select.in.commander");
  @NonNls public static final String FAVORITES = IdeBundle.message("select.in.favorites");
  @NonNls public static final String J2EE = J2EEBundle.message("select.in.j2ee");

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
    /*
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
    */

    Arrays.sort(targets, new Comparator<SelectInTarget>() {
      public int compare(final SelectInTarget o1, final SelectInTarget o2) {
        if (o1.getWeight() < o2.getWeight()) return -1;
        if (o1.getWeight() > o2.getWeight()) return 1;
        return 0;
      }
    });

    return targets;
  }

  @Nullable
  public SelectInTarget getTarget(@NotNull String name) {
    for (SelectInTarget target : myTargets) {
      if (name.equals(target.toString())) return target;
    }
    return null;
  }

  public static SelectInManager getInstance(Project project) {
    return project.getComponent(SelectInManager.class);
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    myOrder.clear();
    for (Iterator iterator = parentNode.getChildren(ELEMENT_TARGET).iterator(); iterator.hasNext();) {
      Element element = (Element)iterator.next();
      myOrder.add(element.getAttributeValue(ATTRIBUTE_NAME));
    }
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    for (Iterator iterator = myOrder.iterator(); iterator.hasNext();) {
      String targetName = (String)iterator.next();
      Element e = new Element(ELEMENT_TARGET);
      e.setAttribute(ATTRIBUTE_NAME, targetName);
      parentNode.addContent(e);
    }
  }

  public String getComponentName() {
    return "SelectInManager";
  }

}
