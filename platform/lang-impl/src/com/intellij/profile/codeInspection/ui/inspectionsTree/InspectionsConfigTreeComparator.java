/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 14-May-2009
 */
package com.intellij.profile.codeInspection.ui.inspectionsTree;

import com.intellij.profile.codeInspection.ui.ToolDescriptors;

import java.util.Comparator;

public class InspectionsConfigTreeComparator implements Comparator<InspectionConfigTreeNode> {
  @Override
  public int compare(InspectionConfigTreeNode o1, InspectionConfigTreeNode o2) {
    String s1 = null;
    String s2 = null;
    Object userObject1 = o1.getUserObject();
    Object userObject2 = o2.getUserObject();

    if (userObject1 instanceof String && userObject2 instanceof String) {
      s1 = (String)userObject1;
      s2 = (String)userObject2;
    } else {
      if (userObject1 instanceof String) return -1;
      if (userObject2 instanceof String) return 1;
    }

    if (s1 != null) {
      return getDisplayTextToSort(s1).compareToIgnoreCase(getDisplayTextToSort(s2));
    }

    final ToolDescriptors descriptors1 = o1.getDescriptors();
    final ToolDescriptors descriptors2 = o2.getDescriptors();
    if (descriptors1 != null && descriptors2 != null) {
      s1 = descriptors1.getDefaultDescriptor().getText();
      s2 = descriptors2.getDefaultDescriptor().getText();
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