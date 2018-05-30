// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.settings.mappings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.BeforeAfter;
import com.intellij.util.ThreeState;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
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
  public ThreeState isSimilar(@NotNull UserDefinedJsonSchemaConfiguration.Item itemLeft,
                              @NotNull UserDefinedJsonSchemaConfiguration.Item itemRight) {
    if (itemLeft.isPattern() != itemRight.isPattern()) return ThreeState.NO;
    if (itemLeft.isPattern()) return comparePatterns(itemLeft, itemRight);
    return comparePaths(itemLeft, itemRight);
  }

  private ThreeState comparePaths(UserDefinedJsonSchemaConfiguration.Item left, UserDefinedJsonSchemaConfiguration.Item right) {
    String leftPath = left.getPath();
    String rightPath = right.getPath();

    if (leftPath.startsWith("mock:///") || rightPath.startsWith("mock:///")) {
      return leftPath.equals(rightPath) ? ThreeState.YES : ThreeState.NO;
    }
    final File leftFile = new File(myProject.getBasePath(), leftPath);
    final File rightFile = new File(myProject.getBasePath(), rightPath);

    if (left.isDirectory()) {
      if (FileUtil.isAncestor(leftFile, rightFile, true)) return ThreeState.YES;
    }
    if (right.isDirectory()) {
      if (FileUtil.isAncestor(rightFile, leftFile, true)) return ThreeState.YES;
    }
    return FileUtil.filesEqual(leftFile, rightFile) && left.isDirectory() == right.isDirectory() ? ThreeState.YES : ThreeState.NO;
  }

  private static ThreeState comparePatterns(@NotNull final UserDefinedJsonSchemaConfiguration.Item leftItem,
                                            @NotNull final UserDefinedJsonSchemaConfiguration.Item rightItem) {
    if (leftItem.getPath().equals(rightItem.getPath())) return ThreeState.YES;
    final BeforeAfter<String> left = getBeforeAfterAroundWildCards(leftItem.getPath());
    final BeforeAfter<String> right = getBeforeAfterAroundWildCards(rightItem.getPath());
    if (left == null || right == null) {
      if (left == null && right == null) return leftItem.getPath().equals(rightItem.getPath()) ? ThreeState.YES : ThreeState.NO;
      if (left == null) {
        return checkOneSideWithoutWildcard(leftItem, right);
      }
      return checkOneSideWithoutWildcard(rightItem, left);
    }
    if (!StringUtil.isEmptyOrSpaces(left.getBefore()) && !StringUtil.isEmptyOrSpaces(right.getBefore())) {
      if (left.getBefore().startsWith(right.getBefore()) || right.getBefore().startsWith(left.getBefore())) {
        return ThreeState.YES;
      }
      // otherwise they are different
      return ThreeState.NO;
    }
    if (!StringUtil.isEmptyOrSpaces(left.getAfter()) && !StringUtil.isEmptyOrSpaces(right.getAfter())) {
      if (left.getAfter().endsWith(right.getAfter()) || right.getAfter().endsWith(left.getAfter())) {
        return ThreeState.YES;
      }
      // otherwise they are different
      return ThreeState.NO;
    }
    return ThreeState.UNSURE;
  }

  @NotNull
  private static ThreeState checkOneSideWithoutWildcard(UserDefinedJsonSchemaConfiguration.Item item, BeforeAfter<String> beforeAfter) {
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
    return new BeforeAfter<>(pattern.substring(0, firstIdx), pattern.substring(lastIdx + 1));
  }
}
