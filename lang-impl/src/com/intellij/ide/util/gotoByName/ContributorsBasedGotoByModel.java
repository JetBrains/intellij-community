/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.NavigationItemListCellRenderer;
import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.util.*;

/**
 * Contributor-based goto model
 */
public abstract class ContributorsBasedGotoByModel implements ChooseByNameModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.ContributorsBasedGotoByModel");

  protected final Project myProject;
  private final ChooseByNameContributor[] myContributors;

  protected ContributorsBasedGotoByModel(Project project, ChooseByNameContributor[] contributors) {
    myProject = project;
    myContributors = contributors;
  }

  public ListCellRenderer getListCellRenderer() {
    return new NavigationItemListCellRenderer();
  }

  public String[] getNames(boolean checkBoxState) {
    Set<String> names = new HashSet<String>();
    for (ChooseByNameContributor contributor : myContributors) {
      try {
        names.addAll(Arrays.asList(contributor.getNames(myProject, checkBoxState)));
      }
      catch(ProcessCanceledException ex) {
        // index corruption detected, ignore
      }
      catch(Exception ex) {
        LOG.error(ex);
      }
    }

    return names.toArray(new String[names.size()]);
  }

  /**
   * Get elements by name from contributors.
   *
   * @param name a name
   * @param checkBoxState if true, non-project files are considered as well
   * @param pattern a pattern to use
   * @return a list of navigation items from contributors for
   *  which {@link #acceptItem(NavigationItem) returns true.
   *
   */
  public Object[] getElementsByName(String name, boolean checkBoxState, final String pattern) {
    List<NavigationItem> items = null;
    for (ChooseByNameContributor contributor : myContributors) {
      try {
        for(NavigationItem item : contributor.getItemsByName(name, pattern, myProject, checkBoxState)) {
          if(acceptItem(item)) {
            if (items == null) {
              items = new ArrayList<NavigationItem>(2);
            }
            items.add(item);
          }
        }
      }
      catch(ProcessCanceledException ex) {
        // index corruption detected, ignore
      }
      catch(Exception ex) {
        LOG.error(ex);
      }
    }
    return items == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : items.toArray(new Object[items.size()]);
  }

  public String getElementName(Object element) {
    return ((NavigationItem)element).getName();
  }

  protected ChooseByNameContributor[] getContributors() {
    return myContributors;
  }

  /**
   * This method allows exetending classes to introduce additional filtering criteria to model
   * beyoud pattern and project/non-project files. The default implementation just returns true.
   * @param item an item to filter
   * @return true if the item is acceptable according to additional filtering criteria.
   */
  protected boolean acceptItem(NavigationItem item) {
    return true;
  }
}