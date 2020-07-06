// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class PluginNode implements IdeaPluginDescriptor {
  public enum Status {
    UNKNOWN, INSTALLED, DOWNLOADED, DELETED
  }

  private PluginId id;
  private String name;
  private String productCode;
  private Date releaseDate;
  private int releaseVersion;
  private boolean licenseOptional;
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
  private List<IdeaPluginDependency> myDependencies = new ArrayList<>();
  private Status myStatus = Status.UNKNOWN;
  private boolean myLoaded;
  private String myDownloadUrl;
  private String myRepositoryName;
  private String myInstalledVersion;
  private boolean myEnabled = true;
  private String myRating;
  private boolean myIncomplete;
  private List<String> myTags;
  private String externalUpdateId;
  private String externalPluginId;

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
  public boolean isLicenseOptional() {
    return licenseOptional;
  }

  public void setLicenseOptional(boolean optional) {
    this.licenseOptional = optional;
  }

  /**
   * Plugin update unique ID from Marketplace database.
   * Needed for getting Plugin meta information.
   */
  @Nullable
  public String getExternalUpdateId() {
    return externalUpdateId;
  }

  public void setExternalUpdateId(String externalUpdateId) {
    this.externalUpdateId = externalUpdateId;
  }

  /**
   * Plugin unique ID from Marketplace storage.
   * Needed for getting Plugin meta information.
   */
  @Nullable
  public String getExternalPluginId() {
    return externalPluginId;
  }

  public void setExternalPluginId(String externalPluginId) {
    this.externalPluginId = externalPluginId;
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

  public Status getStatus() {
    return myStatus;
  }

  public void setStatus(Status status) {
    myStatus = status;
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
    this.date = Long.valueOf(date);
  }

  public long getDate() {
    return date;
  }
  
  /**
   * @deprecated Use {@link #setDependencies(List)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  public void setDepends(@NotNull List<? extends PluginId> depends, PluginId @Nullable [] optionalDependencies) {
    myDependencies = new ArrayList<>();
    for (PluginId id : depends) {
      myDependencies.add(new PluginNodeDependency(id, false));
    }
    if (optionalDependencies != null) {
      for (PluginId dependency : optionalDependencies) {
        myDependencies.add(new PluginNodeDependency(dependency, true));
      }
    }
  }

  public void setDependencies(@NotNull List<IdeaPluginDependency> dependencies) {
    myDependencies = new ArrayList<>(dependencies);
  }

  public void addDepends(@NotNull String id) {
    addDepends(id, false);
  }

  public void addDepends(@NotNull String id, boolean optional) {
    myDependencies.add(new PluginNodeDependency(PluginId.getId(id), optional));
  }

  @Override
  public @NotNull List<IdeaPluginDependency> getDependencies() {
    return myDependencies;
  }

  public List<String> getTags() {
    return myTags;
  }

  public void setTags(@NotNull List<String> tags) {
    myTags = new ArrayList<>(tags);
  }

  public void addTags(@NotNull String tag) {
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
  public Path getPluginPath() {
    return null;
  }

  @Override
  public PluginId @NotNull [] getOptionalDependentPluginIds() {
    List<PluginId> result = new ArrayList<>();
    for (IdeaPluginDependency dependency : myDependencies) {
      if (dependency.isOptional()) {
        result.add(dependency.getPluginId());
      }
    }
    return result.toArray(PluginId.EMPTY_ARRAY);
  }

  @Override
  @Nullable
  public String getResourceBundleBaseName() {
    return null;
  }

  @Override
  public String getUntilBuild() {
    return untilBuild;
  }

  public void setUntilBuild(final String untilBuild) {
    this.untilBuild = untilBuild;
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }

  @Override
  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
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

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof PluginNode && id == ((PluginNode)o).id;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return getName();
  }

  private static class PluginNodeDependency implements IdeaPluginDependency {
    private final PluginId myPluginId;
    private final boolean myOptional;

    private PluginNodeDependency(PluginId id, boolean optional) {
      myPluginId = id;
      myOptional = optional;
    }

    @Override
    public PluginId getPluginId() {
      return myPluginId;
    }

    @Override
    public boolean isOptional() {
      return myOptional;
    }
  }
}