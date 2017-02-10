/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.ColumnInfo;

import javax.swing.*;
import java.util.List;
import java.util.TreeSet;

/**
 * @author stathik
 * @since Dec 26, 2003
 */
public class AvailablePluginsTableModel extends PluginTableModel {
  public static final String ALL = "All";
  public static final String JETBRAINS_REPO = "JetBrains Plugin Repository";
  public static final String BUILTIN_REPO = "Built-in Plugin Repository";

  private String myCategory = ALL;
  private TreeSet<String> myAvailableCategories = new TreeSet<>();
  private String myRepository = ALL;
  private String myVendor = null;

  public AvailablePluginsTableModel() {
    columns = new ColumnInfo[]{new AvailablePluginColumnInfo(this)};
    setSortKey(new RowSorter.SortKey(getNameColumn(), SortOrder.ASCENDING));
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

  public void setVendor(String vendor) {
    myVendor = vendor;
    filter("");
  }

  @Override
  public boolean isPluginDescriptorAccepted(IdeaPluginDescriptor descriptor) {
    final String category = descriptor.getCategory();
    if (category != null){
      if (!ALL.equals(myCategory) && !category.equals(myCategory)) return false;
    }

    if (myVendor != null) {
      final String vendor = descriptor.getVendor();
      if (vendor == null || !StringUtil.containsIgnoreCase(vendor, myVendor)) {
        return false;
      }
    }

    return isHostAccepted(((PluginNode)descriptor).getRepositoryName());
  }

  public boolean isHostAccepted(String repositoryName) {
    if (repositoryName != null) {
      if (repositoryName.equals(ApplicationInfoEx.getInstanceEx().getBuiltinPluginsUrl()) && myRepository.equals(BUILTIN_REPO)) {
        return true;
      }
      else if (!ALL.equals(myRepository) && !repositoryName.equals(myRepository)) {
        return false;
      }
    }
    else {
      return ALL.equals(myRepository) || JETBRAINS_REPO.equals(myRepository);
    }
    return true;
  }

  public TreeSet<String> getAvailableCategories() {
    return myAvailableCategories;
  }

  public String getRepository() {
    return myRepository;
  }

  private static void updateStatus(final IdeaPluginDescriptor descr) {
    if (descr instanceof PluginNode) {
      IdeaPluginDescriptor existing = PluginManager.getPlugin(descr.getPluginId());
      if (existing != null) {
        PluginNode node = (PluginNode)descr;
        node.setStatus(PluginNode.STATUS_INSTALLED);
        node.setInstalledVersion(existing.getVersion());
      }
    }
  }

  public void updatePluginsList(List<IdeaPluginDescriptor> list) {
    view.clear();
    myAvailableCategories.clear();
    filtered.clear();

    //  For each downloadable plugin we need to know whether its counterpart
    //  is already installed, and if yes compare the difference in versions:
    //  availability of newer versions will be indicated separately.
    for (IdeaPluginDescriptor descr : list) {
      updateStatus(descr);
      view.add(descr);
      final String category = descr.getCategory();
      if (category != null) {
        myAvailableCategories.add(category);
      }
      else {
        myAvailableCategories.add(AvailablePluginsManagerMain.N_A);
      }
    }

    fireTableDataChanged();
  }

  public int getNameColumn() {
    return 0;
  }
}
