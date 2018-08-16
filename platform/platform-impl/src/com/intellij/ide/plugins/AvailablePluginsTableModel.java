// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
  private final TreeSet<String> myAvailableCategories = new TreeSet<>();
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

  @Override
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

  @Override
  public int getNameColumn() {
    return 0;
  }
}
