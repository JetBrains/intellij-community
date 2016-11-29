/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.plugins;

import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.extensions.PluginId;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author stathik
 * @since Mar 27, 2003
 */
public class PluginNode implements IdeaPluginDescriptor {
  public static final int STATUS_UNKNOWN = 0;
  public static final int STATUS_INSTALLED = 1;
  public static final int STATUS_MISSING = 2;
  public static final int STATUS_CURRENT = 3;
  public static final int STATUS_NEWEST = 4;
  public static final int STATUS_DOWNLOADED = 5;
  public static final int STATUS_DELETED = 6;

  private PluginId id;
  private String name;
  private String version;
  private String vendor;
  private String description;
  private String sinceBuild;
  private String untilBuild;
  private String changeNotes;
  private String downloads;
  private String category;
  private String size;
  private String vendorEmail;
  private String vendorUrl;
  private String url;
  private long date = Long.MAX_VALUE;
  private List<PluginId> myDependencies;
  private PluginId[] myOptionalDependencies;
  private int myStatus = STATUS_UNKNOWN;
  private boolean myLoaded = false;
  private String myDownloadUrl;
  private String myRepositoryName;
  private String myInstalledVersion;
  private boolean myEnabled = true;
  private String myRating;
  private boolean myIncomplete;

  public PluginNode() { }

  public PluginNode(PluginId id) {
    this.id = id;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    if (id == null) {
      id = PluginId.getId(name);
    }
    this.name = name;
  }

  public void setId(String id) {
    this.id = PluginId.getId(id);
  }

  public String getCategory() {
    return category;
  }

  /**
   * Be careful when comparing Plugins versions. Use
   * PluginManagerColumnInfo.compareVersion() for version comparing.
   *
   * @return Return plugin version
   */
  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getVendor() {
    return vendor;
  }

  public void setVendor(String vendor) {
    this.vendor = vendor;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getChangeNotes() {
    return changeNotes;
  }

  public void setChangeNotes(String changeNotes) {
    this.changeNotes = changeNotes;
  }

  public String getSinceBuild() {
    return sinceBuild;
  }

  public void setSinceBuild(String sinceBuild) {
    this.sinceBuild = sinceBuild;
  }

  /**
   * In complex environment use PluginManagerColumnInfo.getRealNodeState () method instead.
   *
   * @return Status of plugin
   */
  public int getStatus() {
    return myStatus;
  }

  public void setStatus(int status) {
    this.myStatus = status;
  }

  public String toString() {
    return getName();
  }

  public boolean isLoaded() {
    return myLoaded;
  }

  public void setLoaded(boolean loaded) {
    this.myLoaded = loaded;
  }

  public String getDownloads() {
    return downloads;
  }

  public void setDownloads(String downloads) {
    this.downloads = downloads;
  }

  public String getSize() {
    return size;
  }

  public void setSize(String size) {
    this.size = size;
  }

  public String getVendorEmail() {
    return vendorEmail;
  }

  public void setVendorEmail(String vendorEmail) {
    this.vendorEmail = vendorEmail;
  }

  public String getVendorUrl() {
    return vendorUrl;
  }

  public void setVendorUrl(String vendorUrl) {
    this.vendorUrl = vendorUrl;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public void setDate(String date) {
    this.date = Long.valueOf(date).longValue();
  }

  public long getDate() {
    return date;
  }

  public int hashCode() {
    return name.hashCode();
  }

  public boolean equals(Object object) {
    return object instanceof PluginNode && name.equals(((PluginNode)object).getName());
  }

  public List<PluginId> getDepends() {
    return myDependencies;
  }

  public void setDepends(List<PluginId> depends, @Nullable PluginId[] optionalDependencies) {
    this.myDependencies = depends;
    myOptionalDependencies = optionalDependencies;
  }

  public void addDepends(String id) {
    if (this.myDependencies == null) {
      this.myDependencies = new ArrayList<>();
    }
    this.myDependencies.add(PluginId.getId(id));
  }

  /**
   * Methods below implement PluginDescriptor and IdeaPluginDescriptor interface
   */
  public PluginId getPluginId() {
    return id;
  }

  @Nullable
  public ClassLoader getPluginClassLoader() {
    return null;
  }

  @Nullable
  public File getPath() {
    return null;
  }

  @NotNull
  public PluginId[] getDependentPluginIds() {
    return PluginId.EMPTY_ARRAY;
  }

  @NotNull
  public PluginId[] getOptionalDependentPluginIds() {
    return myOptionalDependencies != null ? myOptionalDependencies : PluginId.EMPTY_ARRAY;
  }

  @Nullable
  public String getResourceBundleBaseName() {
    return null;
  }

  @Nullable
  public List<Element> getActionsDescriptionElements() {
    return null;
  }

  @NotNull
  public ComponentConfig[] getAppComponents() {
    throw new IllegalStateException();
  }

  @NotNull
  public ComponentConfig[] getProjectComponents() {
    throw new IllegalStateException();
  }

  @NotNull
  public ComponentConfig[] getModuleComponents() {
    throw new IllegalStateException();
  }

  @NotNull
  public HelpSetPath[] getHelpSets() {
    throw new IllegalStateException();
  }

  @Nullable
  public String getVendorLogoPath() {
    return null;
  }

  public boolean getUseIdeaClassLoader() {
    return false;
  }

  public String getUntilBuild() {
    return untilBuild;
  }

  public void setUntilBuild(final String untilBuild) {
    this.untilBuild = untilBuild;
  }

  public boolean isBundled() {
    return false;
  }

  @Override
  public boolean allowBundledUpdate() {
    return false;
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  @Override
  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  @Nullable
  public String getStatusText() {
    switch (myStatus) {
      case STATUS_UNKNOWN:
        return "Available";
      case STATUS_INSTALLED:
        return "Installed";
      case STATUS_NEWEST:
        return "Ready to update";
      default:
        return null;
    }
  }

  public String getDownloadUrl() {
    return myDownloadUrl;
  }

  public void setDownloadUrl(String host) {
    myDownloadUrl = host;
  }

  public String getRepositoryName() {
    return myRepositoryName;
  }

  public void setRepositoryName(String repositoryName) {
    myRepositoryName = repositoryName;
  }

  public String getInstalledVersion() {
    return myInstalledVersion;
  }

  public void setInstalledVersion(String installedVersion) {
    myInstalledVersion = installedVersion;
  }

  public String getRating() {
    return myRating;
  }

  public void setRating(String rating) {
    myRating = rating;
  }

  public boolean isIncomplete() {
    return myIncomplete;
  }

  public void setIncomplete(boolean incomplete) {
    myIncomplete = incomplete;
  }
}
