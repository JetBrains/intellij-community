// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.inspection.custom;

import com.intellij.find.FindModel;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.SmartList;
import org.intellij.lang.annotations.MagicConstant;
import org.intellij.lang.regexp.RegExpBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

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
  private boolean cleanup;

  public RegExpInspectionConfiguration(@NotNull String name) {
    this.name = name;
    patterns = new SmartList<>();
  }

  @SuppressWarnings("unused")
  public RegExpInspectionConfiguration() {
    patterns = new SmartList<>();
  }

  private RegExpInspectionConfiguration(RegExpInspectionConfiguration other) {
    patterns = new SmartList<>();
    for (InspectionPattern pattern : other.patterns) {
      patterns.add(pattern.copy());
    }
    name = other.name;
    description = other.description;
    uuid = other.uuid;
    suppressId = other.suppressId;
    problemDescriptor = other.problemDescriptor;
    cleanup = other.cleanup;
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

  public boolean isCleanup() {
    return cleanup;
  }

  public void setCleanup(boolean cleanup) {
    this.cleanup = cleanup;
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

  enum RegExpFlag {
    UNIX_LINES(Pattern.UNIX_LINES, 'd'),
    CASE_INSENSITIVE(Pattern.CASE_INSENSITIVE, 'i'),
    MULTILINE(Pattern.MULTILINE, 'm'),
    DOTALL(Pattern.DOTALL, 's'),
    UNICODE_CASE(Pattern.UNICODE_CASE, 'u'),
    CANONICAL_EQUIVALENCE(Pattern.CANON_EQ, null),
    UNICODE_CHARACTER_CLASS(Pattern.UNICODE_CHARACTER_CLASS, 'U'),
    COMMENTS(Pattern.COMMENTS, 'x'),
    LITERAL(Pattern.LITERAL, null);

    public final int id;
    public final @Nullable Character mnemonic;

    RegExpFlag(int id, @Nullable Character mnemonic) {
      this.id = id;
      this.mnemonic = mnemonic;
    }

    public @NotNull @Nls String getText() {
      return switch (this) {
        case UNIX_LINES -> RegExpBundle.message("regexp.dialog.flag.unix.lines");
        case CASE_INSENSITIVE -> RegExpBundle.message("regexp.dialog.flag.case.insensitive");
        case MULTILINE -> RegExpBundle.message("regexp.dialog.flag.multiline");
        case DOTALL -> RegExpBundle.message("regexp.dialog.flag.dotall");
        case UNICODE_CASE -> RegExpBundle.message("regexp.dialog.flag.unicode.case");
        case CANONICAL_EQUIVALENCE -> RegExpBundle.message("regexp.dialog.flag.canonical.equivalence");
        case UNICODE_CHARACTER_CLASS -> RegExpBundle.message("regexp.dialog.flag.unicode.character.class");
        case COMMENTS -> RegExpBundle.message("regexp.dialog.flag.comments");
        case LITERAL -> RegExpBundle.message("regexp.dialog.flag.literal");
      };
    }

    public @NotNull @Nls String getDescription() {
      String description = switch (this) {
        case UNIX_LINES -> RegExpBundle.message("regexp.dialog.flag.unix.lines.description");
        case CASE_INSENSITIVE -> RegExpBundle.message("regexp.dialog.flag.case.insensitive.description");
        case MULTILINE -> RegExpBundle.message("regexp.dialog.flag.multiline.description");
        case DOTALL -> RegExpBundle.message("regexp.dialog.flag.dotall.description");
        case UNICODE_CASE -> RegExpBundle.message("regexp.dialog.flag.unicode.case.description");
        case CANONICAL_EQUIVALENCE -> RegExpBundle.message("regexp.dialog.flag.canonical.equivalence.description");
        case UNICODE_CHARACTER_CLASS -> RegExpBundle.message("regexp.dialog.flag.unicode.character.class.description");
        case COMMENTS -> RegExpBundle.message("regexp.dialog.flag.comments.description");
        case LITERAL -> RegExpBundle.message("regexp.dialog.flag.literal.description");
      };
      return HtmlChunk.html().child(HtmlChunk.div("width: 200px").addRaw(description)).toString();
    }
  }

  public static final class InspectionPattern {
    public static final InspectionPattern EMPTY_REPLACE_PATTERN = new InspectionPattern("", null, 0, FindModel.SearchContext.ANY, "");
    public @NotNull String regExp;
    private @Nullable FileType fileType;
    public @Nullable String _fileType;
    public @NotNull FindModel.SearchContext searchContext;
    public @Nullable String replacement;
    public @MagicConstant(flagsFromClass = Pattern.class) int flags;

    public InspectionPattern(
      @NotNull String regExp,
      @Nullable FileType fileType,
      int flags,
      @NotNull FindModel.SearchContext searchContext,
      @Nullable String replacement
    ) {
      this.regExp = regExp;
      this.fileType = fileType;
      if (this.fileType != null) {
        _fileType = this.fileType.getName();
      }
      this.flags = flags;
      this.searchContext = searchContext;
      this.replacement = replacement;
    }

    @SuppressWarnings("unused")
    public InspectionPattern() {
    }

    InspectionPattern(InspectionPattern copy) {
      regExp = copy.regExp;
      fileType = copy.fileType;
      _fileType = copy._fileType;
      flags = copy.flags;
      searchContext = copy.searchContext;
      replacement = copy.replacement;
    }

    public InspectionPattern copy() {
      return new InspectionPattern(this);
    }

    public @NlsSafe String regExp() {
      return regExp;
    }

    public @Nullable FileType fileType() {
      if (fileType == null && _fileType != null) {
        fileType = FileTypeManager.getInstance().findFileTypeByName(_fileType);
        if (fileType == null) {
          fileType = UnknownFileType.INSTANCE;
        }
      }
      return fileType;
    }

    public FindModel.SearchContext searchContext() {
      return searchContext;
    }

    public @NlsSafe @Nullable String replacement() {
      return replacement;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj == null || obj.getClass() != getClass()) return false;
      var that = (InspectionPattern)obj;
      return Objects.equals(regExp, that.regExp) &&
             Objects.equals(fileType, that.fileType) &&
             Objects.equals(flags, that.flags) &&
             Objects.equals(searchContext, that.searchContext) &&
             Objects.equals(replacement, that.replacement);
    }

    @Override
    public int hashCode() {
      return Objects.hash(regExp, fileType, flags, searchContext, replacement);
    }

    @Override
    public String toString() {
      return "InspectionPattern[" +
             "regExp=" + regExp + ", " +
             "fileType=" + fileType + ", " +
             "flags=" + flags + ", " +
             "searchContext=" + searchContext + ", " +
             "replacement=" + replacement + ']';
    }
  }
}
