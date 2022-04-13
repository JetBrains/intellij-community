// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class PluginNode implements IdeaPluginDescriptor {

  private static final DecimalFormat K_FORMAT = new DecimalFormat("###.#K");
  private static final DecimalFormat M_FORMAT = new DecimalFormat("###.#M");

  public enum Status {
    UNKNOWN, INSTALLED, DOWNLOADED, DELETED
  }

  private @NotNull PluginId id;
  private String name;
  private String productCode;
  private Date releaseDate;
  private int releaseVersion;
  private boolean licenseOptional;
  private String version;
  private String vendor;
  private String organization;
  private @NlsSafe String description;
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
  private @NonNls String myChannel; // TODO parameters map?
  private String myRepositoryName;
  private String myInstalledVersion;
  private boolean myEnabled = true;
  private String myRating;
  private boolean myIncomplete;
  private List<String> myTags;
  private String externalUpdateId;
  private String externalPluginId;

  /**
   * @deprecated Use {@link #PluginNode(PluginId)}
   */
  @Deprecated
  public PluginNode() {
  }

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
    this.name = name;
  }

  public void setId(@NotNull String id) {
    this.id = PluginId.getId(id);
  }

  @Override
  public @Nullable String getProductCode() {
    return productCode;
  }

  public void setProductCode(@Nullable String productCode) {
    this.productCode = productCode;
  }

  @Override
  public @Nullable Date getReleaseDate() {
    return releaseDate;
  }

  public void setReleaseDate(@Nullable Date date) {
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
  public @Nullable String getExternalUpdateId() {
    return externalUpdateId;
  }

  public void setExternalUpdateId(@Nullable String externalUpdateId) {
    this.externalUpdateId = externalUpdateId;
  }

  /**
   * Plugin unique ID from Marketplace storage.
   * Needed for getting Plugin meta information.
   */
  public @Nullable String getExternalPluginId() {
    return externalPluginId;
  }

  public void setExternalPluginId(@Nullable String externalPluginId) {
    this.externalPluginId = externalPluginId;
  }

  @Override
  public @Nullable String getCategory() {
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
  public @Nullable String getVendor() {
    return vendor;
  }

  public void setVendor(@Nullable String vendor) {
    this.vendor = vendor;
  }

  @Override
  public @Nullable String getOrganization() {
    return organization;
  }

  public void setOrganization(@Nullable String organization) {
    this.organization = organization;
  }

  @Override
  public @Nullable String getDescription() {
    return description;
  }

  public void setDescription(@Nullable @NlsSafe String description) {
    this.description = description;
  }

  @Override
  public @Nullable String getChangeNotes() {
    return changeNotes;
  }

  public void setChangeNotes(@Nullable String changeNotes) {
    this.changeNotes = changeNotes;
  }

  @Override
  public @Nullable String getSinceBuild() {
    return sinceBuild;
  }

  public void setSinceBuild(@Nullable String sinceBuild) {
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
  public @Nullable @NlsSafe String getDownloads() {
    return downloads;
  }

  public void setDownloads(@Nullable String downloads) {
    this.downloads = downloads;
  }

  public @Nullable @NlsSafe String getPresentableDownloads() {
    String downloads = getDownloads();

    if (!StringUtil.isEmptyOrSpaces(downloads)) {
      try {
        long value = Long.parseLong(downloads);
        return value <= 1000 ?
               Long.toString(value) :
               value < 1000000 ?
               K_FORMAT.format(value / 1000D) :
               M_FORMAT.format(value / 1000000D);
      }
      catch (NumberFormatException ignore) {
      }
    }

    return null;
  }

  public String getSize() {
    return size;
  }

  public void setSize(String size) {
    this.size = size;
  }

  public long getIntegerSize() {
    try {
      return Long.parseLong(size);
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  public @Nullable @NlsSafe String getPresentableSize() {
    long size = getIntegerSize();

    return size >= 0 ?
           StringUtil.formatFileSize(size).toUpperCase(Locale.ENGLISH) :
           null;
  }

  @Override
  public @Nullable String getVendorEmail() {
    return vendorEmail;
  }

  public void setVendorEmail(@Nullable String vendorEmail) {
    this.vendorEmail = vendorEmail;
  }

  @Override
  public @Nullable String getVendorUrl() {
    return vendorUrl;
  }

  public void setVendorUrl(@Nullable String vendorUrl) {
    this.vendorUrl = vendorUrl;
  }

  @Override
  public @Nullable String getUrl() {
    return url;
  }

  public void setUrl(@Nullable String url) {
    this.url = url;
  }

  public void setDate(String date) {
    this.date = Long.parseLong(date);
  }

  public void setDate(Long date) {
    this.date = date;
  }

  public long getDate() {
    return date;
  }

  public @Nullable @NlsSafe String getPresentableDate() {
    long date = getDate();

    return date > 0 && date != Long.MAX_VALUE ?
           PluginManagerConfigurable.DATE_FORMAT.format(new Date(date)) :
           null;
  }

  /**
   * @deprecated Use {@link #setDependencies(List)} instead
   */
  @Deprecated(forRemoval = true)
  public void setDepends(@NotNull List<PluginId> depends, PluginId @Nullable [] optionalDependencies) {
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

  public void setDependencies(@NotNull List<? extends IdeaPluginDependency> dependencies) {
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

  @Override
  public @Nullable String getDescriptorPath() {
    return null;
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
  public @NotNull PluginId getPluginId() {
    return id;
  }

  @Override
  public @Nullable ClassLoader getPluginClassLoader() {
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
  public @Nullable String getResourceBundleBaseName() {
    return null;
  }

  @Override
  public @Nullable String getUntilBuild() {
    return untilBuild;
  }

  public void setUntilBuild(@Nullable String untilBuild) {
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

  @ApiStatus.Experimental
  public @NonNls String getChannel() {
    return myChannel;
  }

  @ApiStatus.Experimental
  public void setChannel(@NonNls String channel) {
    myChannel = channel;
  }

  public @NlsSafe String getRepositoryName() {
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

  public @Nullable @NlsSafe String getPresentableRating() {
    String rating = getRating();

    if (!StringUtil.isEmptyOrSpaces(rating)) {
      try {
        if (Double.parseDouble(rating) > 0) {
          return StringUtil.trimEnd(rating, ".0");
        }
      }
      catch (NumberFormatException ignore) {
      }
    }
    return null;
  }

  public boolean isIncomplete() {
    return myIncomplete;
  }

  public void setIncomplete(boolean incomplete) {
    myIncomplete = incomplete;
  }

  public boolean detailsLoaded() {
    return externalPluginId == null || externalUpdateId == null || description != null;
  }

  @Override
  public boolean equals(Object o) {
    return this == o ||
           o instanceof PluginNode && id.equals(((PluginNode)o).id);
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
    private final @NotNull PluginId myPluginId;
    private final boolean myOptional;

    private PluginNodeDependency(@NotNull PluginId id, boolean optional) {
      myPluginId = id;
      myOptional = optional;
    }

    @Override
    public @NotNull PluginId getPluginId() {
      return myPluginId;
    }

    @Override
    public boolean isOptional() {
      return myOptional;
    }
  }
}