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
package com.intellij.ide.util.importProject;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.ide.highlighter.ModuleFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
*         Date: Jul 13, 2007
*/
public class ModuleDescriptor {
  private String myName;
  private final Map<File, Set<File>> myContentToSourceRoots = new HashMap<File, Set<File>>();
  private final Set<File> myLibraryFiles = new HashSet<File>();
  private final Set<ModuleDescriptor> myDependencies = new HashSet<ModuleDescriptor>();
  private static final String[] ourModuleNameStoplist = new String[] {
      "java", "src", "source", "sources", "C:", "D:", "E:", "F:", "temp", "tmp"
  };
  private boolean myReuseExistingElement;

  public ModuleDescriptor(final File contentRoot, final Set<File> sourceRoots) {
    myName = suggestModuleName(contentRoot);
    myContentToSourceRoots.put(contentRoot, sourceRoots);
  }

  public void reuseExisting(boolean reuseExistingElement) {
    myReuseExistingElement = reuseExistingElement;
  }

  public boolean isReuseExistingElement() {
    return myReuseExistingElement;
  }

  private static String suggestModuleName(final File contentRoot) {
    for (File dir = contentRoot; dir != null; dir = dir.getParentFile()) {
      final String suggestion = dir.getName();
      boolean belongsToStopList = false;
      for (String undesirableName : ourModuleNameStoplist) {
        if (suggestion.equalsIgnoreCase(undesirableName)) {
          belongsToStopList = true;
          break;
        }
      }
      if (!belongsToStopList) {
        return StringUtil.capitalize(suggestion);
      }
    }
    
    return StringUtil.capitalize(contentRoot.getName());
  }

  public String getName() {
    return myName;
  }

  public void setName(final String name) {
    myName = name;
  }

  public Set<File> getContentRoots() {
    return Collections.unmodifiableSet(myContentToSourceRoots.keySet());
  }

  public Set<File> getSourceRoots() {
    final Set<File> allSources = new HashSet<File>();
    for (Set<File> files : myContentToSourceRoots.values()) {
      allSources.addAll(files);
    }
    return allSources;
  }

  public Set<File> getSourceRoots(File contentRoot) {
    final Set<File> sources = myContentToSourceRoots.get(contentRoot);
    return (sources != null) ? Collections.unmodifiableSet(sources) : Collections.<File>emptySet();
  }
  
  public void addContentRoot(File contentRoot) {
    myContentToSourceRoots.put(contentRoot, new HashSet<File>());
  }
  
  public Set<File> removeContentRoot(File contentRoot) {
    return myContentToSourceRoots.remove(contentRoot);
  }
  
  public void addSourceRoot(final File contentRoot, File sourceRoot) {
    Set<File> sources = myContentToSourceRoots.get(contentRoot);
    if (sources == null) {
      sources = new HashSet<File>();
      myContentToSourceRoots.put(contentRoot, sources);
    }
    sources.add(sourceRoot);
  }
  
  public void addDependencyOn(ModuleDescriptor dependence) {
    myDependencies.add(dependence);
  }
  
  public void removeDependencyOn(ModuleDescriptor module) {
    myDependencies.remove(module);
  }
  
  public void addLibraryFile(File libFile) {
    myLibraryFiles.add(libFile);
  }

  public Set<File> getLibraryFiles() {
    return myLibraryFiles;
  }

  public Set<ModuleDescriptor> getDependencies() {
    return Collections.unmodifiableSet(myDependencies);
  }

  /**
   * For debug purposes only
   */
  public String toString() {
    @NonNls final StringBuilder builder = new StringBuilder();
    builder.append("[Module: ").append(getContentRoots()).append(" | ");
    for (File sourceRoot : getSourceRoots()) {
      builder.append(sourceRoot.getName()).append(",");
    }
    builder.append("]");
    return builder.toString();
  }

  public void clearModuleDependencies() {
    myDependencies.clear();
  }

  public void clearLibraryFiles() {
    myLibraryFiles.clear();
  }

  @NotNull
  public String computeModuleFilePath() throws InvalidDataException {
    final String name = getName();
    final Set<File> contentRoots = getContentRoots();
    if (contentRoots.size() > 0) {
      return contentRoots.iterator().next().getPath() + File.separator + name + ModuleFileType.DOT_DEFAULT_EXTENSION;
    }
    else {
      throw new InvalidDataException("Module " + name + " has no content roots and will not be created.");
    }
  }
}
