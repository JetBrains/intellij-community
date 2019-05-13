// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.extensions.PluginId;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author stathik
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
  private String productCode;
  private Date releaseDate;
  private int releaseVersion;
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
  private boolean myLoaded;
  private String myDownloadUrl;
  private String myRepositoryName;
  private String myInstalledVersion;
  private boolean myEnabled = true;
  private String myRating;
  private boolean myIncomplete;
  private List<String> myTags;

  public PluginNode() { }

  public PluginNode(@NotNull PluginId id) {
    this.id = id;
  }

  public PluginNode(@NotNull PluginId id, String name, String size) {
    this.id = id;
    this.name = name;
    this.size = size;
  }

  public void setCategory(@NotNull String category) {
    this.category = category;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
    if (id == null) {
      id = PluginId.getId(name);
    }
    this.name = name;
  }

  public void setId(@NotNull String id) {
    this.id = PluginId.getId(id);
  }

  @Nullable
  @Override
  public String getProductCode() {
    return productCode;
  }

  public void setProductCode(String productCode) {
    this.productCode = productCode;
  }

  @Nullable
  @Override
  public Date getReleaseDate() {
    return releaseDate;
  }

  public void setReleaseDate(Date date) {
    releaseDate = date;
  }

  @Override
  public int getReleaseVersion() {
    return releaseVersion;
  }

  public void setReleaseVersion(int releaseVersion) {
    this.releaseVersion = releaseVersion;
  }

  @Override
  public String getCategory() {
    return category;
  }

  /**
   * Be careful when comparing Plugins versions. Use
   * PluginManagerColumnInfo.compareVersion() for version comparing.
   *
   * @return Return plugin version
   */
  @Override
  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @Override
  public String getVendor() {
    return vendor;
  }

  public void setVendor(@NotNull String vendor) {
    this.vendor = vendor;
  }

  @Override
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public String getChangeNotes() {
    return changeNotes;
  }

  public void setChangeNotes(@NotNull String changeNotes) {
    this.changeNotes = changeNotes;
  }

  @Override
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
    myStatus = status;
  }

  @Override
  public String toString() {
    return getName();
  }

  public boolean isLoaded() {
    return myLoaded;
  }

  public void setLoaded(boolean loaded) {
    myLoaded = loaded;
  }

  @Override
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

  @Override
  public String getVendorEmail() {
    return vendorEmail;
  }

  public void setVendorEmail(String vendorEmail) {
    this.vendorEmail = vendorEmail;
  }

  @Override
  public String getVendorUrl() {
    return vendorUrl;
  }

  public void setVendorUrl(String vendorUrl) {
    this.vendorUrl = vendorUrl;
  }

  @Override
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

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public boolean equals(Object object) {
    return object instanceof PluginNode && name.equals(((PluginNode)object).getName());
  }

  public List<PluginId> getDepends() {
    return myDependencies;
  }

  public void setDepends(@NotNull List<? extends PluginId> depends, @Nullable PluginId[] optionalDependencies) {
    myDependencies = new ArrayList<>(depends);
    myOptionalDependencies = optionalDependencies;
  }

  public void addDepends(@NotNull String id) {
    (myDependencies != null ? myDependencies : (myDependencies = new ArrayList<>())).add(PluginId.getId(id));
  }

  public List<String> getTags() {
    return myTags;
  }

  public void setTags(@NotNull List<String> tags) {
    myTags = new ArrayList<>(tags);
  }

  void addTags(@NotNull String tag) {
    (myTags != null ? myTags : (myTags = new ArrayList<>())).add(tag);
  }

  /**
   * Methods below implement PluginDescriptor and IdeaPluginDescriptor interface
   */
  @Override
  public PluginId getPluginId() {
    return id;
  }

  @Override
  @Nullable
  public ClassLoader getPluginClassLoader() {
    return null;
  }

  @Override
  @Nullable
  public File getPath() {
    return null;
  }

  @Override
  @NotNull
  public PluginId[] getDependentPluginIds() {
    return PluginId.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PluginId[] getOptionalDependentPluginIds() {
    return myOptionalDependencies != null ? myOptionalDependencies : PluginId.EMPTY_ARRAY;
  }

  @Override
  @Nullable
  public String getResourceBundleBaseName() {
    return null;
  }

  @Override
  @Nullable
  public List<Element> getActionsDescriptionElements() {
    return null;
  }

  @Override
  @NotNull
  public List<ComponentConfig> getAppComponents() {
    throw new IllegalStateException();
  }

  @Override
  @NotNull
  public List<ComponentConfig> getProjectComponents() {
    throw new IllegalStateException();
  }

  @Override
  @NotNull
  public List<ComponentConfig> getModuleComponents() {
    throw new IllegalStateException();
  }

  @Override
  @NotNull
  public HelpSetPath[] getHelpSets() {
    throw new IllegalStateException();
  }

  @Override
  @Nullable
  public String getVendorLogoPath() {
    return null;
  }

  @Override
  public boolean getUseIdeaClassLoader() {
    return false;
  }

  @Override
  public String getUntilBuild() {
    return untilBuild;
  }

  public void setUntilBuild(final String untilBuild) {
    this.untilBuild = untilBuild;
  }

  @Override
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
