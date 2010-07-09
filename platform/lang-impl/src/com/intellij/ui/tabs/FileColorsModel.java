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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopeManager;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.FileColorManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
// todo[spL]: listen to scope rename
public class FileColorsModel implements Cloneable {
  public static final String FILE_COLOR = "fileColor";

  private final List<FileColorConfiguration> myConfigurations;
  private final List<FileColorConfiguration> mySharedConfigurations;

  private final Project myProject;

  FileColorsModel(@NotNull final Project project) {
    myProject = project;
    myConfigurations = new ArrayList<FileColorConfiguration>();
    mySharedConfigurations = new ArrayList<FileColorConfiguration>();
  }

  private FileColorsModel(@NotNull final Project project,
                          @NotNull final List<FileColorConfiguration> regular,
                          @NotNull final List<FileColorConfiguration> shared) {
    myProject = project;
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

  public void remove(@NotNull final String scopeName, boolean shared) {
    final List<FileColorConfiguration> configurations = shared ? mySharedConfigurations : myConfigurations;
    final FileColorConfiguration configuration = findConfiguration(scopeName, shared);

    if (configuration != null) {
      configurations.remove(configuration);
    }
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

    if (FileColorManager.getInstance(myProject).isHighlightNonProjectFiles()
        && !isFileUnderProject(psiFile.getVirtualFile())) {
      return FileColorManager.OUT_OF_PROJECT_SCOPE_COLOR; 
    }
    return null;
  }

  private boolean isFileUnderProject(@Nullable VirtualFile file) {
    if (file == null) return false;
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    return myProject.isInitialized() && !fileIndex.isIgnored(file) && fileIndex.getContentRootForFile(file) != null;
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

  public void remove(FileColorConfiguration configuration, boolean shared) {
    final List<FileColorConfiguration> configurations = shared ? mySharedConfigurations : myConfigurations;
    configurations.remove(configuration);
  }

  public void setConfigurations(final List<FileColorConfiguration> configurations, final boolean shared) {
    if (shared) {
      mySharedConfigurations.clear();
      mySharedConfigurations.addAll(configurations);
    }
    else {
      myConfigurations.clear();
      myConfigurations.addAll(configurations);
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
