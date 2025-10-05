// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.*;
import com.intellij.psi.util.JavaMultiReleaseUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A scope decorator that allows to prefer a particular Java class version from multi-release Jar dependencies
 */
public final class JavaVersionBasedScope extends DelegatingGlobalSearchScope {
  private final LanguageLevel myLevel;

  public JavaVersionBasedScope(@NotNull Project project,
                               @NotNull GlobalSearchScope baseScope,
                               @NotNull LanguageLevel desiredLevel) {
    super(project, baseScope);
    // Set desired level to Java 8 if it's less than Java 8, to make all these scopes equal, so they could be deduplicated
    myLevel = desiredLevel.isLessThan(JavaMultiReleaseUtil.MIN_MULTI_RELEASE_VERSION)
              ? JavaMultiReleaseUtil.MAX_NON_MULTI_RELEASE_VERSION
              : desiredLevel;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return super.contains(file) && checkMultiReleaseJar(file);
  }

  private boolean checkMultiReleaseJar(@NotNull VirtualFile file) {
    // Do not filter directories, as they may contain non-versioned files
    if (file.isDirectory()) return true;
    VirtualFile baseFile = JavaMultiReleaseUtil.findBaseFile(file);
    if (myLevel.isLessThan(JavaMultiReleaseUtil.MIN_MULTI_RELEASE_VERSION)) {
      // In pre-multi-release
      return baseFile == null;
    }
    if (baseFile == null) {
      baseFile = file;
    }
    VirtualFile specificFile = JavaMultiReleaseUtil.findVersionSpecificFile(baseFile, myLevel);
    return file.equals(specificFile);
  }

  @ApiStatus.Experimental
  @Override
  public @NotNull CodeInsightContextInfo getCodeInsightContextInfo() {
    CodeInsightContextInfo info = super.getCodeInsightContextInfo();
    if (info instanceof NoContextInformation) {
      return info;
    }

    ActualCodeInsightContextInfo actualInfo = (ActualCodeInsightContextInfo)info;
    return new ActualCodeInsightContextInfo() {
      @Override
      public boolean contains(@NotNull VirtualFile file, @NotNull CodeInsightContext context) {
        return actualInfo.contains(file, context) && checkMultiReleaseJar(file);
      }

      @Override
      public @NotNull CodeInsightContextFileInfo getFileInfo(@NotNull VirtualFile file) {
        CodeInsightContextFileInfo fileInfo = actualInfo.getFileInfo(file);
        if (fileInfo instanceof DoesNotContainFileInfo) {
          return fileInfo;
        }
        if (!checkMultiReleaseJar(file)) {
          return CodeInsightContextAwareSearchScopes.DoesNotContainFileInfo();
        }
        return fileInfo;
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    JavaVersionBasedScope scope = (JavaVersionBasedScope)o;
    return myLevel == scope.myLevel;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Objects.hashCode(myLevel);
    return result;
  }

  @Override
  public String toString() {
    return "Java " + myLevel.feature() + " @ " + myBaseScope;
  }
}
