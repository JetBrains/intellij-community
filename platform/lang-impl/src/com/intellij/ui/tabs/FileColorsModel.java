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

package com.intellij.ui.tabs;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.DefaultScopesProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.psi.search.scope.TestsScope;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.ColorUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author spleaner
 * @author Konstantin Bulenkov
 */
// todo[spL]: listen to scope rename
public class FileColorsModel implements Cloneable {
  public static final String FILE_COLOR = "fileColor";

  private final List<FileColorConfiguration> myConfigurations;
  private final List<FileColorConfiguration> mySharedConfigurations;
  private static final Map<String, String> globalScopes;
  private static Map<String, String> globalScopesColors;
  static {
    globalScopes = new HashMap<String, String>();
    globalScopes.put(NonProjectFilesScope.NAME, "file.colors.enable.non.project");
    globalScopes.put(TestsScope.NAME, "file.colors.enable.tests");

    globalScopesColors = new HashMap<String, String>();
  }

  private final Project myProject;

  FileColorsModel(@NotNull final Project project) {
    myProject = project;
    myConfigurations = new ArrayList<FileColorConfiguration>();
    mySharedConfigurations = new ArrayList<FileColorConfiguration>();

    if (globalScopesColors.size() < globalScopes.size()) {
      final DefaultScopesProvider defaultScopesProvider = DefaultScopesProvider.getInstance(project);
      for (String scopeName : globalScopes.keySet()) {
        final NamedScope scope = defaultScopesProvider.findCustomScope(scopeName);
        assert scope != null : "There is no custom scope with name " + scopeName;
        final Color color = ColorUtil.getColor(scope.getClass());
        assert color != null : scope.getClass().getName() + " is not annotated with @Colored";
        globalScopesColors.put(scopeName, ColorUtil.toHex(color));
      }
    }
    initGlobalScopes();
  }

  private FileColorsModel(@NotNull final Project project,
                          @NotNull final List<FileColorConfiguration> regular,
                          @NotNull final List<FileColorConfiguration> shared) {
    myProject = project;
    myConfigurations = new ArrayList<FileColorConfiguration>();
    mySharedConfigurations = new ArrayList<FileColorConfiguration>();
    myConfigurations.addAll(regular);
    mySharedConfigurations.addAll(shared);
    initGlobalScopes();
  }

  private void initGlobalScopes() {
    for (String scopeName : globalScopes.keySet()) {
      if (findConfiguration(scopeName, false) == null) {
        final String color = PropertiesComponent.getInstance().getOrInit(globalScopes.get(scopeName), globalScopesColors.get(scopeName));
        if (color.length() != 0) {
          myConfigurations.add(new FileColorConfiguration(scopeName, color));
        }
      }
    }
  }

  public void save(final Element e, final boolean shared) {
    final List<FileColorConfiguration> configurations = shared ? mySharedConfigurations : myConfigurations;
    for (final FileColorConfiguration configuration : configurations) {
      final String name = configuration.getScopeName();
      if (!shared && globalScopes.containsKey(name)) {
        PropertiesComponent.getInstance().setValue(name, configuration.getColorName());
      } else {
        configuration.save(e);
      }
    }
  }

  public void load(final Element e, final boolean shared) {
    List<FileColorConfiguration> configurations = shared ? mySharedConfigurations : myConfigurations;

    configurations.clear();

    final List<Element> list = (List<Element>)e.getChildren(FILE_COLOR);
    final Map<String, String> global = new HashMap<String, String>(globalScopes);
    for (Element child : list) {
      final FileColorConfiguration configuration = FileColorConfiguration.load(child);
      if (configuration != null) {
        if (!shared) {
          final String name = configuration.getScopeName();
          if (globalScopes.get(name) != null) {
            global.remove(name);
          }
        }
        configurations.add(configuration);
      }
    }

    if (!shared) {
      final PropertiesComponent properties = PropertiesComponent.getInstance();
      for (String scope : global.keySet()) {
        final String colorName = properties.getValue(scope);
        if (colorName != null) {
          configurations.add(new FileColorConfiguration(scope, colorName));
        }
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

    return new FileColorsModel(myProject, regular, shared);
  }

  public void add(@NotNull final FileColorConfiguration configuration, boolean shared) {
    final List<FileColorConfiguration> configurations = shared ? mySharedConfigurations : myConfigurations;
    if (!configurations.contains(configuration)) {
      configurations.add(configuration);
    }
  }

  public void add(@NotNull final String scopeName, @NotNull final String colorName, boolean shared) {
    final FileColorConfiguration configuration = new FileColorConfiguration();
    configuration.setScopeName(scopeName);
    configuration.setColorName(colorName);

    add(configuration, shared);
  }

  @Nullable
  private FileColorConfiguration findConfiguration(final String scopeName, final boolean shared) {
    final List<FileColorConfiguration> configurations = shared ? mySharedConfigurations : myConfigurations;
    for (final FileColorConfiguration configuration : configurations) {
      if (scopeName.equals(configuration.getScopeName())) {
        return configuration;
      }
    }

    return null;
  }

  public boolean isShared(@NotNull final String scopeName) {
    return findConfiguration(scopeName, true) != null;
  }

  @Nullable
  public String getColor(@NotNull PsiFile psiFile) {
    if (!psiFile.isValid()) {
      return null;
    }

    final FileColorConfiguration configuration = findConfiguration(psiFile);
    if (configuration != null && configuration.isValid(psiFile.getProject())) {
      return configuration.getColorName();
    }
    return null;
  }

  @Nullable
  private FileColorConfiguration findConfiguration(@NotNull final PsiFile colored) {
    for (final FileColorConfiguration configuration : myConfigurations) {
      final NamedScope scope = NamedScopeManager.getScope(myProject, configuration.getScopeName());
      if (scope != null) {
        final NamedScopesHolder namedScopesHolder = NamedScopeManager.getHolder(myProject, configuration.getScopeName(), null);
        if (scope.getValue() != null && namedScopesHolder != null && scope.getValue().contains(colored, namedScopesHolder)) {
          return configuration;
        }
      }
    }

    for (FileColorConfiguration configuration : mySharedConfigurations) {
      final NamedScope scope = NamedScopeManager.getScope(myProject, configuration.getScopeName());
      if (scope != null) {
        final NamedScopesHolder namedScopesHolder = NamedScopeManager.getHolder(myProject, configuration.getScopeName(), null);
        if (scope.getValue() != null && namedScopesHolder != null && scope.getValue().contains(colored, namedScopesHolder)) {
          return configuration;
        }
      }
    }

    return null;
  }

  public boolean isShared(FileColorConfiguration configuration) {
    return mySharedConfigurations.contains(configuration);
  }

  public void setConfigurations(final List<FileColorConfiguration> configurations, final boolean shared) {
    if (shared) {
      mySharedConfigurations.clear();
      mySharedConfigurations.addAll(configurations);
    }
    else {
      myConfigurations.clear();
      final HashMap<String, String> global = new HashMap<String, String>(globalScopes);
      for (FileColorConfiguration configuration : configurations) {
        myConfigurations.add(configuration);
        final String name = configuration.getScopeName();
        if (global.containsKey(name)) {
          PropertiesComponent.getInstance().setValue(global.get(name), configuration.getColorName());
          global.remove(name);
        }
      }
      for (String name : global.keySet()) {
        PropertiesComponent.getInstance().setValue(global.get(name), "");
      }
    }
  }

  public boolean isColored(String scopeName, boolean shared) {
    return findConfiguration(scopeName, shared) != null;
  }

  public List<FileColorConfiguration> getLocalConfigurations() {
    return myConfigurations;
  }

  public List<FileColorConfiguration> getSharedConfigurations() {
    return mySharedConfigurations;
  }
}
