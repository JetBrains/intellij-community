// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.workspace.jps.entities.LibraryEntity;
import com.intellij.platform.workspace.jps.entities.SdkEntity;
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator;
import com.intellij.workspaceModel.ide.LibraryEntities;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
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

  public abstract @NotNull PatternBasedPackageSet updatePattern(@NotNull String oldName, @NotNull String newName);

  public abstract @NotNull PatternBasedPackageSet updateModulePattern(@NotNull String oldName, @NotNull String newName);

  public String getModulePattern() {
    return myModulePatternText;
  }

  private static @NotNull Pattern convertToPattern(String text) {
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
      for (LibraryEntity library : fileIndex.findContainingLibraries(file)) {
        final String libraryName = LibraryNameGenerator.INSTANCE.getLegacyLibraryName(library.getSymbolicId());
        if (libraryName != null) {
          if (libPattern.matcher(libraryName).matches()) return true;
        } else {
          final String presentableName = LibraryEntities.getPresentableName(library);
          final String fileName = new File(presentableName).getName();
          if (libPattern.matcher(fileName).matches()) return true;
        }
      }
      for (SdkEntity sdk : fileIndex.findContainingSdks(file)) {
        if (libPattern.matcher(sdk.getName()).matches()) return true;
      }
      return false;
    }
    return true;
  }
}
