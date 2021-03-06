/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

public abstract class PatternBasedPackageSet extends PackageSetBase {
  protected final Pattern myModulePattern;
  protected final Pattern myModuleGroupPattern;
  protected final String myModulePatternText;

  public PatternBasedPackageSet(@NonNls String modulePatternText) {
    myModulePatternText = modulePatternText;
    Pattern moduleGroupPattern = null;
    Pattern modulePattern = null;
    if (modulePatternText != null && !modulePatternText.isEmpty()) {
      if (modulePatternText.startsWith("group:")) {
        int index = modulePatternText.indexOf(':', 6);
        if (index == -1) index = modulePatternText.length();
        moduleGroupPattern = convertToPattern(modulePatternText.substring(6, index));
        if (index < modulePatternText.length() - 1) {
          modulePattern = convertToPattern(modulePatternText.substring(index + 1));
        }
      }
      else {
        modulePattern = convertToPattern(modulePatternText);
      }
    }
    myModulePattern = modulePattern;
    myModuleGroupPattern = moduleGroupPattern;
  }

  protected boolean matchesModule(final VirtualFile file, final ProjectFileIndex fileIndex) {
    final Module module = fileIndex.getModuleForFile(file);
    if (module != null) {
      if (myModulePattern != null && myModulePattern.matcher(module.getName()).matches()) return true;
      if (myModuleGroupPattern != null) {
        final String[] groupPath = ModuleManager.getInstance(module.getProject()).getModuleGroupPath(module);
        if (groupPath != null) {
          for (String node : groupPath) {
            if (myModuleGroupPattern.matcher(node).matches()) return true;
          }
        }
      }
    }
    return myModulePattern == null && myModuleGroupPattern == null;
  }

  public abstract String getPattern();

  public abstract boolean isOn(String oldQName);

  @NotNull
  public abstract PatternBasedPackageSet updatePattern(@NotNull String oldName, @NotNull String newName);

  @NotNull
  public abstract PatternBasedPackageSet updateModulePattern(@NotNull String oldName, @NotNull String newName);

  public String getModulePattern() {
    return myModulePatternText;
  }

  @NotNull
  private static Pattern convertToPattern(String text) {
    StringBuilder builder = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (c == ' ' || Character.isLetter(c) || Character.isDigit(c) || c == '_') {
        builder.append(c);
      }
      else if (c == '*') {
        builder.append(".*");
      }
      else {
        builder.append('\\').append(c);
      }
    }

    return Pattern.compile(builder.toString());
  }

  protected static boolean matchesLibrary(final Pattern libPattern,
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
