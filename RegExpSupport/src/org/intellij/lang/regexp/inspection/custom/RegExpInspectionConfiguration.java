// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection.custom;

import com.intellij.find.FindModel;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Bas Leijdekkers
 */
public class RegExpInspectionConfiguration implements Comparable<RegExpInspectionConfiguration> {

  public List<InspectionPattern> patterns; // keep public for settings serialization
  private String name;
  private String description;
  private String uuid;
  private String suppressId;
  private String problemDescriptor;

  public RegExpInspectionConfiguration(@NotNull String name) {
    this.name = name;
    patterns = new SmartList<>();
  }

  @SuppressWarnings("unused")
  public RegExpInspectionConfiguration() {
    patterns = new SmartList<>();
  }

  public RegExpInspectionConfiguration(RegExpInspectionConfiguration other) {
    patterns = new SmartList<>(other.patterns);
    name = other.name;
    description = other.description;
    uuid = other.uuid;
    suppressId = other.suppressId;
    problemDescriptor = other.problemDescriptor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RegExpInspectionConfiguration that = (RegExpInspectionConfiguration)o;
    return Objects.equals(uuid, that.uuid);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  public RegExpInspectionConfiguration copy() {
    return new RegExpInspectionConfiguration(this);
  }

  public List<InspectionPattern> getPatterns() {
    return patterns;
  }

  public void addPattern(InspectionPattern pattern) {
    if (!patterns.contains(pattern)) {
      patterns.add(pattern);
    }
  }

  public void removePattern(InspectionPattern pattern) {
    patterns.remove(pattern);
  }

  public @NlsSafe String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
    if (uuid == null && this.name != null) { // name can be null on deserializing from settings
      uuid = UUID.nameUUIDFromBytes(this.name.getBytes(StandardCharsets.UTF_8)).toString();
    }
    this.name = name;
  }

  public @NlsSafe String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getUuid() {
    if (uuid == null && name != null) { // name can be null on deserializing from settings
      uuid = UUID.nameUUIDFromBytes(this.name.getBytes(StandardCharsets.UTF_8)).toString();
    }
    return uuid;
  }

  public void setUuid(@Nullable String uuid) {
    this.uuid = uuid;
  }

  public @NlsSafe String getSuppressId() {
    return suppressId;
  }

  public void setSuppressId(String suppressId) {
    this.suppressId = suppressId;
  }

  public @NlsSafe String getProblemDescriptor() {
    return problemDescriptor;
  }

  public void setProblemDescriptor(String problemDescriptor) {
    this.problemDescriptor = problemDescriptor;
  }

  @Override
  public int compareTo(@NotNull RegExpInspectionConfiguration o) {
    int result = name.compareToIgnoreCase(o.name);
    if (result == 0) {
      if (uuid == null) {
        if (o.uuid != null) {
          result = -1;
        }
      }
      else if (o.uuid == null) {
        result = 1;
      }
      else {
        result = uuid.compareTo(o.uuid);
      }
    }
    return result;
  }

  public static final class InspectionPattern {
    public static final InspectionPattern EMPTY_REPLACE_PATTERN = new InspectionPattern("", null, FindModel.SearchContext.ANY, "");
    public @NotNull String regExp;
    private @Nullable FileType fileType;
    public @Nullable String _fileType;
    public @NotNull FindModel.SearchContext searchContext;
    public @Nullable String replacement;

    public InspectionPattern(
      @NotNull String regExp,
      @Nullable FileType fileType,
      @NotNull FindModel.SearchContext searchContext,
      @Nullable String replacement
    ) {
      this.regExp = regExp;
      this.fileType = fileType;
      if (this.fileType != null) {
        _fileType = this.fileType.getName();
      }
      this.searchContext = searchContext;
      this.replacement = replacement;
    }

    @SuppressWarnings("unused")
    public InspectionPattern() {
    }

    public @NlsSafe String regExp() { return regExp; }

    public @Nullable FileType fileType() {
      if (fileType == null && _fileType != null) {
        fileType = FileTypeManager.getInstance().findFileTypeByName(_fileType);
        if (fileType == null) {
          fileType = UnknownFileType.INSTANCE;
        }
      }
      return fileType;
    }

    public FindModel.SearchContext searchContext() { return searchContext; }

    public @NlsSafe @Nullable String replacement() { return replacement; }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj == null || obj.getClass() != getClass()) return false;
      var that = (InspectionPattern)obj;
      return Objects.equals(regExp, that.regExp) &&
             Objects.equals(fileType, that.fileType) &&
             Objects.equals(searchContext, that.searchContext) &&
             Objects.equals(replacement, that.replacement);
    }

    @Override
    public int hashCode() {
      return Objects.hash(regExp, fileType, searchContext, replacement);
    }

    @Override
    public String toString() {
      return "InspectionPattern[" +
             "regExp=" + regExp + ", " +
             "fileType=" + fileType + ", " +
             "searchContext=" + searchContext + ", " +
             "replacement=" + replacement + ']';
    }
  }
}
