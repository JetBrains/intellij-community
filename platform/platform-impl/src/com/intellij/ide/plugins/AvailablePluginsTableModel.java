/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 19-Aug-2006
 * Time: 14:54:29
 */
package com.intellij.ide.plugins;

import com.intellij.util.ui.ColumnInfo;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 26, 2003
 * Time: 3:51:58 PM
 * To change this template use Options | File Templates.
 */
public class AvailablePluginsTableModel extends PluginTableModel {

  public static final String ALL = "All";
  private String myCategory = ALL;
  private LinkedHashSet<String> myAvailableCategories = new LinkedHashSet<String>();

  protected static final String STATUS = "Status";

  public static final String JETBRAINS_REPO = "JetBrains";
  private String myRepository = ALL;

  public AvailablePluginsTableModel() {
    super.columns = new ColumnInfo[] {
      new AvailablePluginColumnInfo(this),
      new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_DOWNLOADS, this),
      new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_DATE, this),
      new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_CATEGORY, this)};

    setSortKey(new RowSorter.SortKey(getNameColumn(), SortOrder.ASCENDING));
    view = new ArrayList<IdeaPluginDescriptor>();
  }

  public String getCategory() {
    return myCategory;
  }

  public void setCategory(String category, String filter) {
    myCategory = category;
    filter(filter);
  }

  public void setRepository(String repository, String filter) {
    myRepository = repository;
    filter(filter);
  }

  @Override
  public boolean isPluginDescriptorAccepted(IdeaPluginDescriptor descriptor) {
    final String category = descriptor.getCategory();
    if (category != null){
      if (!ALL.equals(myCategory) && !category.equals(myCategory)) return false;
    }

    final String repositoryName = ((PluginNode)descriptor).getRepositoryName();
    if (repositoryName != null) {
      if (!ALL.equals(myRepository) && !repositoryName.equals(myRepository)) return false;
    } else {
      return ALL.equals(myRepository) || JETBRAINS_REPO.equals(myRepository);
    }
    return true;
  }

  public LinkedHashSet<String> getAvailableCategories() {
    return myAvailableCategories;
  }

  public String getRepository() {
    return myRepository;
  }

  private static void updateStatus(final IdeaPluginDescriptor descr) {
    if (descr instanceof PluginNode) {
      final PluginNode node = (PluginNode)descr;
      IdeaPluginDescriptor existing = PluginManager.getPlugin(descr.getPluginId());
      if (existing != null) {
        node.setStatus(PluginNode.STATUS_INSTALLED);
        node.setInstalledVersion(existing.getVersion());
      }
    }
  }

  public void updatePluginsList(List<IdeaPluginDescriptor> list) {
    view.clear();
    myAvailableCategories.clear();

    //  For each downloadable plugin we need to know whether its counterpart
    //  is already installed, and if yes compare the difference in versions:
    //  availability of newer versions will be indicated separately.
    for (IdeaPluginDescriptor descr : list) {
      updateStatus(descr);
      view.add(descr);
      myAvailableCategories.add(descr.getCategory());
    }

    fireTableDataChanged();
  }

  @Override
  public void filter(final List<IdeaPluginDescriptor> filtered) {
    view.clear();
    for (IdeaPluginDescriptor descriptor : filtered) {
      view.add(descriptor);
    }
    super.filter(filtered);
  }

  public int getNameColumn() {
    return 0;
  }

}
