/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.j2ee.ui;

import com.intellij.j2ee.j2eeDom.xmlData.ReadOnlyDeploymentDescriptorModificationException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * author: lesya
 */
public class CompositeCommitable implements Commitable {
  private List<Commitable> myComponents = new ArrayList<Commitable>();

  public void addComponent(Commitable panel) {
    myComponents.add(panel);
  }

  public void commit() throws ReadOnlyDeploymentDescriptorModificationException {
    for (Iterator iterator = myComponents.iterator(); iterator.hasNext();) {
      ((Commitable)iterator.next()).commit();
    }
  }

  public void reset() {
    for (Iterator iterator = myComponents.iterator(); iterator.hasNext();) {
      ((Commitable)iterator.next()).reset();
    }
  }

  public void dispose() {
    for (Iterator iterator = myComponents.iterator(); iterator.hasNext();) {
      ((Commitable)iterator.next()).dispose();
    }
  }

  public List<Warning> getWarnings() {
    ArrayList<Warning> result = new ArrayList<Warning>();
    for (Iterator iterator = myComponents.iterator(); iterator.hasNext();) {
      List<Warning> warnings = ((Commitable)iterator.next()).getWarnings();
      if (warnings != null) {
        result.addAll(warnings);
      }
    }
    return result;
  }
}
