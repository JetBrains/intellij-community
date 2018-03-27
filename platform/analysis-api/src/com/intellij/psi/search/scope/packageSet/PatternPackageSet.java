/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

public class PatternPackageSet extends PatternBasedPackageSet {
  @NonNls public static final String SCOPE_TEST = "test";
  @NonNls public static final String SCOPE_SOURCE = "src";
  @NonNls public static final String SCOPE_LIBRARY = "lib";
  @NonNls public static final String SCOPE_PROBLEM = "problem";
  public static final String SCOPE_ANY = "";

  private final Pattern myPattern;
  private final String myAspectJSyntaxPattern;
  private final String myScope;

  public PatternPackageSet(@NonNls @Nullable String aspectPattern,
                           @NotNull String scope,
                           @NonNls String modulePattern) {
    super(modulePattern);
    myAspectJSyntaxPattern = aspectPattern;
    myScope = scope;
    myPattern = aspectPattern != null ? Pattern.compile(FilePatternPackageSet.convertToRegexp(aspectPattern, '.')) : null;
  }

  @Override
  public boolean contains(VirtualFile file, @NotNull NamedScopesHolder holder) {
    return contains(file, holder.getProject(), holder);
  }

  @Override
  public boolean contains(VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (matchesScope(file, project, fileIndex)) {
      if (myPattern == null) {
        return true;
      }
      String packageName = getPackageName(file, fileIndex);
      if (packageName != null && myPattern.matcher(packageName).matches()) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesScope(VirtualFile file, Project project, ProjectFileIndex fileIndex) {
    if (file == null) return false;
    boolean isSource = fileIndex.isInSourceContent(file);
    if (myScope == SCOPE_ANY) {
      return fileIndex.isInContent(file) && matchesModule(file, fileIndex);
    }
    if (myScope == SCOPE_SOURCE) {
      return isSource && !TestSourcesFilter.isTestSources(file, project) && matchesModule(file, fileIndex);
    }
    if (myScope == SCOPE_LIBRARY) {
      return (fileIndex.isInLibraryClasses(file) || fileIndex.isInLibrarySource(file)) && matchesLibrary(myModulePattern, file, fileIndex);
    }
    if (myScope == SCOPE_TEST) {
      return isSource && TestSourcesFilter.isTestSources(file, project) && matchesModule(file, fileIndex);
    }
    if (myScope == SCOPE_PROBLEM) {
      return isSource && WolfTheProblemSolver.getInstance(project).isProblemFile(file) && matchesModule(file, fileIndex);
    }
    throw new RuntimeException("Unknown scope: " + myScope);
  }

  private static String getPackageName(VirtualFile file, ProjectFileIndex fileIndex) {
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    return StringUtil.getQualifiedName(fileIndex.getPackageNameByDirectory(dir), file.getNameWithoutExtension());
  }

  @NotNull
  @Override
  public PackageSet createCopy() {
    return new PatternPackageSet(myAspectJSyntaxPattern, myScope, myModulePatternText);
  }

  @Override
  public int getNodePriority() {
    return 0;
  }

  @NotNull
  @Override
  public String getText() {
    StringBuilder buf = new StringBuilder();
    if (myScope != SCOPE_ANY) {
      buf.append(myScope);
    }

    if (myModulePattern != null || myModuleGroupPattern != null) {
      buf.append("[").append(myModulePatternText).append("]");
    }

    if (buf.length() > 0) {
      buf.append(':');
    }

    buf.append(myAspectJSyntaxPattern);
    return buf.toString();
  }

  @Override
  public boolean isOn(String oldQName) {
    return Comparing.strEqual(oldQName, myAspectJSyntaxPattern) || //class qname
           Comparing.strEqual(oldQName + "..*", myAspectJSyntaxPattern) || //package req
           Comparing.strEqual(oldQName + ".*", myAspectJSyntaxPattern); //package
  }

  @NotNull
  @Override
  public PatternBasedPackageSet updatePattern(@NotNull String oldName, @NotNull String newName) {
    return new PatternPackageSet(myAspectJSyntaxPattern.replace(oldName, newName), myScope, myModulePatternText);
  }

  @NotNull
  @Override
  public PatternBasedPackageSet updateModulePattern(@NotNull String oldName, @NotNull String newName) {
    return new PatternPackageSet(myAspectJSyntaxPattern, myScope, myModulePatternText.replace(oldName, newName));
  }

  @Override
  public String getPattern() {
    return myAspectJSyntaxPattern;
  }

  private static boolean matchesLibrary(final Pattern libPattern,
                                       final VirtualFile file,
                                       final ProjectFileIndex fileIndex) {
    if (libPattern != null) {
      final List<OrderEntry> entries = fileIndex.getOrderEntriesForFile(file);
      for (OrderEntry orderEntry : entries) {
        if (orderEntry instanceof LibraryOrderEntry) {
          final String libraryName = ((LibraryOrderEntry)orderEntry).getLibraryName();
          if (libraryName != null) {
            if (libPattern.matcher(libraryName).matches()) return true;
          } else {
            final String presentableName = orderEntry.getPresentableName();
            final String fileName = new File(presentableName).getName();
            if (libPattern.matcher(fileName).matches()) return true;
          }
        } else if (orderEntry instanceof JdkOrderEntry) {
          final String jdkName = ((JdkOrderEntry)orderEntry).getJdkName();
          if (jdkName != null && libPattern.matcher(jdkName).matches()) return true;
        }
      }
      return false;
    }
    return true;
  }
}