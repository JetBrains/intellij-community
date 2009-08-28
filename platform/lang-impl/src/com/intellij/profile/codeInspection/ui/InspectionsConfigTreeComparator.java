/*
 * User: anna
 * Date: 14-May-2009
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.Descriptor;

import java.util.Comparator;

public class InspectionsConfigTreeComparator implements Comparator<InspectionConfigTreeNode> {
  public int compare(InspectionConfigTreeNode o1, InspectionConfigTreeNode o2) {
    String s1 = null;
    String s2 = null;
    Object userObject1 = o1.getUserObject();
    Object userObject2 = o2.getUserObject();

    if (userObject1 instanceof String && userObject2 instanceof String) {
      s1 = (String)userObject1;
      s2 = (String)userObject2;
    }

    final Descriptor descriptor1 = o1.getDesriptor();
    final Descriptor descriptor2 = o2.getDesriptor();
    if (descriptor1 != null && descriptor2 != null) {
      s1 = descriptor1.getText();
      s2 = descriptor2.getText();
    }

    if (s1 != null && s2 != null) {
      return getDisplayTextToSort(s1).compareToIgnoreCase(getDisplayTextToSort(s2));
    }

    //can't be
    return -1;
  }

  public static String getDisplayTextToSort(String s) {
    if (s.length() == 0) {
      return s;
    }
    while (!Character.isLetterOrDigit(s.charAt(0))) {
      s = s.substring(1);
      if (s.length() == 0) {
        return s;
      }
    }
    return s;
  }
}