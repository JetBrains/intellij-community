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
package com.jetbrains.jsonSchema;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.BeforeAfter;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Irina.Chernushina on 2/17/2016.
 */
public class JsonSchemaPatternComparator {
  @NotNull
  private final Project myProject;

  public JsonSchemaPatternComparator(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public ThreeState isSimilar(@NotNull JsonSchemaMappingsConfigurationBase.Item itemLeft,
                              @NotNull JsonSchemaMappingsConfigurationBase.Item itemRight) {
    if (itemLeft.isPattern() != itemRight.isPattern()) return ThreeState.NO;
    if (itemLeft.isPattern()) return comparePatterns(itemLeft, itemRight);
    return comparePaths(itemLeft, itemRight);
  }

  private ThreeState comparePaths(JsonSchemaMappingsConfigurationBase.Item left, JsonSchemaMappingsConfigurationBase.Item right) {
    final File leftFile = new File(myProject.getBasePath(), left.getPath());
    final File rightFile = new File(myProject.getBasePath(), right.getPath());

    if (left.isDirectory()) {
      if (FileUtil.isAncestor(leftFile, rightFile, true)) return ThreeState.YES;
    }
    if (right.isDirectory()) {
      if (FileUtil.isAncestor(rightFile, leftFile, true)) return ThreeState.YES;
    }
    return FileUtil.filesEqual(leftFile, rightFile) && left.isDirectory() == right.isDirectory() ? ThreeState.YES : ThreeState.NO;
  }

  private static ThreeState comparePatterns(JsonSchemaMappingsConfigurationBase.Item left, JsonSchemaMappingsConfigurationBase.Item right) {
    if (left.getPath().equals(right.getPath())) return ThreeState.YES;
    final BeforeAfter<String> beforeAfterLeft = getBeforeAfterAroundWildCards(left.getPath());
    final BeforeAfter<String> beforeAfterRight = getBeforeAfterAroundWildCards(right.getPath());
    if (beforeAfterLeft == null || beforeAfterRight == null) {
      if (beforeAfterLeft == null && beforeAfterRight == null) return left.getPath().equals(right.getPath()) ? ThreeState.YES : ThreeState.NO;
      if (beforeAfterLeft == null) {
        return checkOneSideWithoutWildcard(left, beforeAfterRight);
      }
      return checkOneSideWithoutWildcard(right, beforeAfterLeft);
    }
    if (!StringUtil.isEmptyOrSpaces(beforeAfterLeft.getBefore()) && !StringUtil.isEmptyOrSpaces(beforeAfterRight.getBefore())) {
      if (beforeAfterLeft.getBefore().startsWith(beforeAfterRight.getBefore()) ||
          beforeAfterRight.getBefore().startsWith(beforeAfterLeft.getBefore())) {
        return ThreeState.YES;
      }
      // otherwise they are different
      return ThreeState.NO;
    }
    if (!StringUtil.isEmptyOrSpaces(beforeAfterLeft.getAfter()) && !StringUtil.isEmptyOrSpaces(beforeAfterRight.getAfter())) {
      if (beforeAfterLeft.getAfter().endsWith(beforeAfterRight.getAfter()) ||
          beforeAfterRight.getAfter().endsWith(beforeAfterLeft.getAfter())) {
        return ThreeState.YES;
      }
      // otherwise they are different
      return ThreeState.NO;
    }
    return ThreeState.UNSURE;
  }

  @NotNull
  private static ThreeState checkOneSideWithoutWildcard(JsonSchemaMappingsConfigurationBase.Item item, BeforeAfter<String> beforeAfter) {
    if (!StringUtil.isEmptyOrSpaces(beforeAfter.getBefore()) && item.getPath().startsWith(beforeAfter.getBefore())) {
      return ThreeState.YES;
    }
    if (!StringUtil.isEmptyOrSpaces(beforeAfter.getAfter()) && item.getPath().endsWith(beforeAfter.getAfter())) {
      return ThreeState.YES;
    }
    return ThreeState.UNSURE;
  }

  @Nullable
  private static BeforeAfter<String> getBeforeAfterAroundWildCards(@NotNull final String pattern) {
    final int firstIdx = pattern.indexOf('*');
    final int lastIdx = pattern.lastIndexOf('*');
    if (firstIdx < 0 || lastIdx < 0) return null;
    return new BeforeAfter<>(pattern.substring(0, firstIdx), pattern.substring(lastIdx + 1, pattern.length()));
  }
}
