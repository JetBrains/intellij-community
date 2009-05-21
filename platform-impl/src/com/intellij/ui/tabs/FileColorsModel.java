package com.intellij.ui.tabs;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author spleaner
 */
public class FileColorsModel implements Cloneable {
  public static final String FILE_COLOR = "fileColor";

  private final List<FileColorConfiguration> myConfigurations;
  private final List<FileColorConfiguration> mySharedConfigurations;

  private Map<VirtualFile, FileColorConfiguration> myFile2ConfigurationCache;

  FileColorsModel() {
    myConfigurations = new ArrayList<FileColorConfiguration>();
    mySharedConfigurations = new ArrayList<FileColorConfiguration>();
  }

  private FileColorsModel(@NotNull final List<FileColorConfiguration> regular, @NotNull final List<FileColorConfiguration> shared) {
    myConfigurations = regular;
    mySharedConfigurations = shared;
  }

  public void save(final Element e, final boolean shared) {
    final List<FileColorConfiguration> configurations = shared ? mySharedConfigurations : myConfigurations;
    for (final FileColorConfiguration configuration : configurations) {
      configuration.save(e);
    }
  }

  public void load(final Element e, final boolean shared) {
    List<FileColorConfiguration> configurations = shared ? mySharedConfigurations : myConfigurations;

    configurations.clear();

    final List<Element> list = (List<Element>)e.getChildren(FILE_COLOR);
    for (Element child : list) {
      final FileColorConfiguration configuration = FileColorConfiguration.load(child);
      if (configuration != null) {
        configurations.add(configuration);
      }
    }
  }

  public FileColorsModel clone() throws CloneNotSupportedException {
    final List<FileColorConfiguration> regular = new ArrayList<FileColorConfiguration>();
    for (final FileColorConfiguration configuration : myConfigurations) {
      regular.add(configuration.clone());
    }

    final ArrayList<FileColorConfiguration> shared = new ArrayList<FileColorConfiguration>();
    for (final FileColorConfiguration sharedConfiguration : mySharedConfigurations) {
      shared.add(sharedConfiguration.clone());
    }

    return new FileColorsModel(regular, shared);
  }

  private void resetCache() {
    myFile2ConfigurationCache = null;
  }

  public void add(@NotNull final FileColorConfiguration configuration) {
    if (!myConfigurations.contains(configuration)) {
      myConfigurations.add(configuration);
      resetCache();
    }
  }

  public void add(@NotNull final VirtualFile file, @NotNull final String colorName) {
    assert file.isValid();

    final FileColorConfiguration c = getMatchedConfiguration(file, true);
    if (c != null) {
      myConfigurations.remove(c);
      mySharedConfigurations.remove(c);
    }

    final FileColorConfiguration configuration = new FileColorConfiguration();
    configuration.setPath(file.getPresentableUrl());
    configuration.setColorName(colorName);
    myConfigurations.add(configuration);

    resetCache();
  }

  public void remove(@NotNull final VirtualFile file) {
    final FileColorConfiguration configuration = getMatchedConfiguration(file, false);
    if (configuration != null) {
      if (!myConfigurations.remove(configuration)) {
        mySharedConfigurations.remove(configuration);
      }

      resetCache();
    }
  }

  private void initCache() {
    myFile2ConfigurationCache = new LinkedHashMap<VirtualFile, FileColorConfiguration>();
    for (List<FileColorConfiguration> cs : new List[]{myConfigurations, mySharedConfigurations}) {
      for (FileColorConfiguration configuration : cs) {
        if (configuration.isValid()) {
          final VirtualFile virtualFile = configuration.resolve();
          if (virtualFile != null) {
            myFile2ConfigurationCache.put(virtualFile, configuration);
          }
        }
      }
    }
  }

  public boolean isShared(VirtualFile file) {
    final FileColorConfiguration configuration = getMatchedConfiguration(file, false);
    return configuration != null && mySharedConfigurations.contains(configuration);
  }

  @Nullable
  private VirtualFile getCached(@NotNull final VirtualFile file, final boolean strict) {
    if (strict) {
      return myFile2ConfigurationCache.containsKey(file) ? file : null;
    }

    VirtualFile candidate = null;
    for (VirtualFile colored : myFile2ConfigurationCache.keySet()) {
      if (colored.isValid()) {
        if (VfsUtil.isAncestor(colored, file, false)) {
          if (candidate == null) {
            candidate = colored;
          }
          else {
            candidate = VfsUtil.isAncestor(candidate, colored, false) ? colored : candidate;
          }
        }
      }
    }

    return candidate;
  }

  @Nullable
  private FileColorConfiguration getMatchedConfiguration(@NotNull final VirtualFile file, final boolean strict) {
    final VirtualFile configuredFile = getMatchedConfiguredFile(file, strict);
    if (configuredFile != null) {
      return myFile2ConfigurationCache.get(configuredFile);
    }

    return null;
  }

  @Nullable
  public VirtualFile getMatchedConfiguredFile(@NotNull final VirtualFile file, final boolean strict) {
    if (myFile2ConfigurationCache == null) {
      initCache();
    }

    return getCached(file, strict);
  }

  public void setShared(final VirtualFile file, boolean shared) {
    final FileColorConfiguration configuration = getMatchedConfiguration(file, false);
    if (configuration != null) {
      setShared(configuration, shared);
    }
  }

  @Nullable
  public String getColor(@NotNull VirtualFile colored, final boolean strict) {
    if (!colored.isValid()) {
      return null;
    }

    final FileColorConfiguration configuration = getMatchedConfiguration(colored, strict);
    if (configuration != null && configuration.isValid()) {
      return configuration.getColorName();
    }

    return null;
  }

  public List<FileColorConfiguration> getConfigurations() {
    final List<FileColorConfiguration> result = new ArrayList<FileColorConfiguration>();

    result.addAll(myConfigurations);
    result.addAll(mySharedConfigurations);

    return result;
  }

  public boolean isShared(FileColorConfiguration configuration) {
    return mySharedConfigurations.contains(configuration);
  }

  public void remove(FileColorConfiguration configuration) {
    myConfigurations.remove(configuration);
    mySharedConfigurations.remove(configuration);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FileColorsModel that = (FileColorsModel)o;

    if (myConfigurations != null ? !myConfigurations.equals(that.myConfigurations) : that.myConfigurations != null) return false;
    if (mySharedConfigurations != null
        ? !mySharedConfigurations.equals(that.mySharedConfigurations)
        : that.mySharedConfigurations != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = myConfigurations != null ? myConfigurations.hashCode() : 0;
    result = 31 * result + (mySharedConfigurations != null ? mySharedConfigurations.hashCode() : 0);
    return result;
  }
  
  public void updateFrom(@NotNull final FileColorsModel model) {
    myConfigurations.clear();
    for (final FileColorConfiguration configuration : model.myConfigurations) {
      try {
        myConfigurations.add(configuration.clone());
      }
      catch (CloneNotSupportedException e) {
        // nothing
      }
    }

    mySharedConfigurations.clear();
    for (final FileColorConfiguration sharedConfiguration : model.mySharedConfigurations) {
      try {
        mySharedConfigurations.add(sharedConfiguration.clone());
      }
      catch (CloneNotSupportedException e) {
        // nothing
      }
    }

    resetCache();
  }

  public void setShared(FileColorConfiguration configuration, boolean shared) {
    if (myConfigurations.contains(configuration) && shared) {
      myConfigurations.remove(configuration);
      if (!mySharedConfigurations.contains(configuration)) {
        mySharedConfigurations.add(configuration);
      }

      resetCache();
    } else if (mySharedConfigurations.contains(configuration) && !shared){
      mySharedConfigurations.remove(configuration);
      if (!myConfigurations.contains(configuration)) {
        myConfigurations.add(configuration);
      }

      resetCache();
    }
  }
}
