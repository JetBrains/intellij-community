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

package com.intellij.ui.tabs;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DefaultScopesProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.psi.search.scope.TestsScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.search.scope.packageSet.PackageSetBase;
import com.intellij.ui.ColorUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author spleaner
 * @author Konstantin Bulenkov
 */
// todo[spL]: listen to scope rename
public class FileColorsModel implements Cloneable {
  public static final String FILE_COLOR = "fileColor";

  private final List<FileColorConfiguration> myApplicationLevelConfigurations;
  private final List<FileColorConfiguration> myProjectLevelConfigurations;
  private static final Map<String, String> predefinedScopeNameToPropertyKey;
  private static final Map<String, String> predefinedScopeNameToColor;

  static {
    predefinedScopeNameToPropertyKey = new THashMap<String, String>();
    predefinedScopeNameToPropertyKey.put(NonProjectFilesScope.NAME, "file.colors.enable.non.project");
    if (PlatformUtils.isIntelliJ() || PlatformUtils.isRubyMine()) {
        predefinedScopeNameToPropertyKey.put(TestsScope.NAME, "file.colors.enable.tests");
    }

    predefinedScopeNameToColor = new THashMap<String, String>();
  }

  @NotNull
  private final Project myProject;

  FileColorsModel(@NotNull final Project project) {
    myProject = project;
    myApplicationLevelConfigurations = new ArrayList<FileColorConfiguration>();
    myProjectLevelConfigurations = new ArrayList<FileColorConfiguration>();

    if (predefinedScopeNameToColor.size() < predefinedScopeNameToPropertyKey.size()) {
      DefaultScopesProvider defaultScopesProvider = DefaultScopesProvider.getInstance(project);
      for (String scopeName : predefinedScopeNameToPropertyKey.keySet()) {
        final NamedScope scope = defaultScopesProvider.findCustomScope(scopeName);
        assert scope != null : "There is no custom scope with name " + scopeName;
        final Color color = ColorUtil.getColor(scope.getClass());
        assert color != null : scope.getClass().getName() + " is not annotated with @Colored";
        final String colorName = FileColorManagerImpl.getColorName(color);
        predefinedScopeNameToColor.put(scopeName, colorName == null ? ColorUtil.toHex(color) : colorName);
      }
    }
    initGlobalScopes();
  }

  private FileColorsModel(@NotNull Project project,
                          @NotNull List<FileColorConfiguration> applicationLevel,
                          @NotNull List<FileColorConfiguration> projectLevel) {
    myProject = project;
    myApplicationLevelConfigurations = new ArrayList<FileColorConfiguration>();
    myProjectLevelConfigurations = new ArrayList<FileColorConfiguration>();
    myApplicationLevelConfigurations.addAll(applicationLevel);
    myProjectLevelConfigurations.addAll(projectLevel);
    initGlobalScopes();
  }

  private void initGlobalScopes() {
    PropertiesComponent propertyComponent = PropertiesComponent.getInstance();
    for (String scopeName : predefinedScopeNameToPropertyKey.keySet()) {
      if (findConfiguration(scopeName, false) == null) {
        String color = propertyComponent.getValue(predefinedScopeNameToPropertyKey.get(scopeName));
        if (color == null) {
          // backward compatibility, previously it was saved incorrectly as scope name instead of specified property key
          color = propertyComponent.getValue(scopeName);
          if (color == null) {
            color = predefinedScopeNameToColor.get(scopeName);
          }
        }

        if (!color.isEmpty()) {
          final Color col = ColorUtil.fromHex(color, null);
          final String name = col == null ? null : FileColorManagerImpl.getColorName(col);
          myApplicationLevelConfigurations.add(new FileColorConfiguration(scopeName, name == null ? color : name));
        }
      }
    }
  }

  public void save(@NotNull Element e, boolean isProjectLevel) {
    List<FileColorConfiguration> configurations = isProjectLevel ? myProjectLevelConfigurations : myApplicationLevelConfigurations;
    for (FileColorConfiguration configuration : configurations) {
      String scopeName = configuration.getScopeName();
      String propertyKey = isProjectLevel ? null : predefinedScopeNameToPropertyKey.get(scopeName);
      if (propertyKey == null) {
        configuration.save(e);
      }
      else {
        PropertiesComponent propertyComponent = PropertiesComponent.getInstance();
        propertyComponent.setValue(propertyKey, configuration.getColorName(), predefinedScopeNameToColor.get(scopeName));
        // previously it was saved incorrectly as scope name instead of specified property key
        PropertiesComponent.getInstance().setValue(scopeName, null);
      }
    }
  }

  public void load(@NotNull Element e, boolean isProjectLevel) {
    List<FileColorConfiguration> configurations = isProjectLevel ? myProjectLevelConfigurations : myApplicationLevelConfigurations;

    configurations.clear();

    Map<String, String> predefinedScopeNameToPropertyKey = new THashMap<String, String>(FileColorsModel.predefinedScopeNameToPropertyKey);
    for (Element child : e.getChildren(FILE_COLOR)) {
      FileColorConfiguration configuration = FileColorConfiguration.load(child);
      if (configuration != null) {
        if (!isProjectLevel) {
          predefinedScopeNameToPropertyKey.remove(configuration.getScopeName());
        }
        configurations.add(configuration);
      }
    }

    if (!isProjectLevel) {
      PropertiesComponent properties = PropertiesComponent.getInstance();
      for (String scopeName : predefinedScopeNameToPropertyKey.keySet()) {
        String colorName = properties.getValue(predefinedScopeNameToPropertyKey.get(scopeName));
        if (colorName == null) {
          // backward compatibility, previously it was saved incorrectly as scope name instead of specified property key
          colorName = properties.getValue(scopeName);

          // so, default value
          if (colorName == null) {
            colorName = predefinedScopeNameToColor.get(scopeName);
          }
        }
        // empty means that value deleted
        if (!StringUtil.isEmpty(colorName)) {
          configurations.add(new FileColorConfiguration(scopeName, colorName));
        }
      }
    }
  }

  @Override
  public FileColorsModel clone() throws CloneNotSupportedException {
    List<FileColorConfiguration> applicationLevel = new ArrayList<FileColorConfiguration>();
    for (FileColorConfiguration configuration : myApplicationLevelConfigurations) {
      applicationLevel.add(configuration.clone());
    }

    List<FileColorConfiguration> projectLevel = new ArrayList<FileColorConfiguration>();
    for (FileColorConfiguration configuration : myProjectLevelConfigurations) {
      projectLevel.add(configuration.clone());
    }
    return new FileColorsModel(myProject, applicationLevel, projectLevel);
  }

  public void add(@NotNull FileColorConfiguration configuration, boolean isProjectLevel) {
    List<FileColorConfiguration> configurations = isProjectLevel ? myProjectLevelConfigurations : myApplicationLevelConfigurations;
    if (!configurations.contains(configuration)) {
      configurations.add(configuration);
    }
  }

  public void add(@NotNull String scopeName, @NotNull String colorName, boolean isProjectLevel) {
    add(new FileColorConfiguration(scopeName, colorName), isProjectLevel);
  }

  @Nullable
  private FileColorConfiguration findConfiguration(@NotNull String scopeName, boolean isProjectLevel) {
    List<FileColorConfiguration> configurations = isProjectLevel ? myProjectLevelConfigurations : myApplicationLevelConfigurations;
    for (FileColorConfiguration configuration : configurations) {
      if (scopeName.equals(configuration.getScopeName())) {
        return configuration;
      }
    }
    return null;
  }

  public boolean isProjectLevel(@NotNull String scopeName) {
    return findConfiguration(scopeName, true) != null;
  }

  @Nullable
  public String getColor(@NotNull PsiFile psiFile) {
    if (!psiFile.isValid()) {
      return null;
    }
    VirtualFile virtualFile = psiFile.getVirtualFile();
    return virtualFile == null ? null : getColor(virtualFile, psiFile.getProject());
  }

  @Nullable
  public String getColor(@NotNull VirtualFile file, Project project) {
    if (!file.isValid()) {
      return null;
    }

    final FileColorConfiguration configuration = findConfiguration(file);
    if (configuration != null && configuration.isValid(project)) {
      return configuration.getColorName();
    }
    return null;
  }
  
  @Nullable 
  public String getScopeColor(@NotNull String scopeName, Project project) {
    FileColorConfiguration configuration = null;
    for (FileColorConfiguration each : getConfigurations()) {
      if (scopeName.equals(each.getScopeName())) {
        configuration = each;
        break;
      }
    }
    if (configuration != null && configuration.isValid(project)) {
      return configuration.getColorName();
    }
    return null;
  }

  @Nullable
  private FileColorConfiguration findConfiguration(@NotNull final VirtualFile colored) {
    for (FileColorConfiguration configuration : getConfigurations()) {
      NamedScope scope = NamedScopesHolder.getScope(myProject, configuration.getScopeName());
      if (scope != null) {
        NamedScopesHolder namedScopesHolder = NamedScopesHolder.getHolder(myProject, configuration.getScopeName(), null);
        PackageSet packageSet = scope.getValue();
        if (packageSet instanceof PackageSetBase && namedScopesHolder != null && ((PackageSetBase)packageSet).contains(colored, myProject, namedScopesHolder)) {
          return configuration;
        }
      }
    }
    return null;
  }
  
  @NotNull
  private List<FileColorConfiguration> getConfigurations() {
    return ContainerUtil.concat(myApplicationLevelConfigurations, myProjectLevelConfigurations);
  }

  public boolean isProjectLevel(@NotNull FileColorConfiguration configuration) {
    return myProjectLevelConfigurations.contains(configuration);
  }

  public void setConfigurations(@NotNull List<FileColorConfiguration> configurations, boolean isProjectLevel) {
    if (isProjectLevel) {
      myProjectLevelConfigurations.clear();
      myProjectLevelConfigurations.addAll(configurations);
    }
    else {
      myApplicationLevelConfigurations.clear();
      Map<String, String> predefinedScopeNameToPropertyKey = new THashMap<String, String>(FileColorsModel.predefinedScopeNameToPropertyKey);
      PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
      for (FileColorConfiguration configuration : configurations) {
        myApplicationLevelConfigurations.add(configuration);
        String propertyKey = predefinedScopeNameToPropertyKey.remove(configuration.getScopeName());
        if (propertyKey != null) {
          propertiesComponent.setValue(propertyKey, configuration.getColorName());
        }
      }
      for (String scopeName : predefinedScopeNameToPropertyKey.keySet()) {
        // empty string means that value deleted
        propertiesComponent.setValue(predefinedScopeNameToPropertyKey.get(scopeName), "");
        // previously it was saved incorrectly as scope name instead of specified property key
        propertiesComponent.setValue(scopeName, null);
      }
    }
  }

  public boolean isColored(@NotNull String scopeName, boolean isProjectLevel) {
    return findConfiguration(scopeName, isProjectLevel) != null;
  }

  public List<FileColorConfiguration> getLocalConfigurations() {
    return myApplicationLevelConfigurations;
  }

  @NotNull
  public List<FileColorConfiguration> getProjectLevelConfigurations() {
    return myProjectLevelConfigurations;
  }
}
