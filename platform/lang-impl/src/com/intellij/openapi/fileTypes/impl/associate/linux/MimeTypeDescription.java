// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl.associate.linux;

import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.impl.associate.OSAssociateFileTypesUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

final class MimeTypeDescription implements Comparable<MimeTypeDescription> {
  private static final String TYPE_PREFIX = "application/x-" + PlatformUtils.getPlatformPrefix() + "-";

  /**
   * See <a href="https://www.freedesktop.org/wiki/Specifications/shared-mime-info-spec">FreeDesktop Shared Mime Info</a>.
   */
  private static final Map<String, String> OS_MIME_TYPES = Map.ofEntries(
    // @formatter:off
    Map.entry("ASP",            "application/x-asp"),
    Map.entry("CMakeLists.txt", "text/x-cmake"),
    Map.entry("CSS",            "text/css"),
    Map.entry("Cucumber",       "text/x-gherkin"),
    Map.entry("DTD",            "application/xml-dtd"),
    Map.entry("Go",             "text/x-go"),
    Map.entry("Groovy",         "text/x-groovy"),
    Map.entry("HTML",           "text/html"),
    Map.entry("JAVA",           "text/x-java"),
    Map.entry("JSON",           "application/json"),
    Map.entry("JavaScript",     "application/javascript"),
    Map.entry("Jupyter",        "application/x-ipynb+json"),
    Map.entry("Log",            "text/x-log"),
    Map.entry("Markdown",       "text/markdown"),
    Map.entry("ObjectiveC",     "text/x-c++src"),
    Map.entry("PHP",            "application/x-php"),
    Map.entry("PLAIN_TEXT",     "text/plain"),
    Map.entry("Python",         "text/x-python"),
    Map.entry("QT UI file",     "application/x-designer"),
    Map.entry("RNG Compact",    "application/relax-ng-compact-syntax"),
    Map.entry("ReST",           "text/x-rst"),
    Map.entry("Ruby",           "application/x-ruby"),
    Map.entry("SCSS",           "text/x-scss"),
    Map.entry("SVG",            "image/svg+xml"),
    Map.entry("Sass",           "text/x-sass"),
    Map.entry("XHTML",          "text/xhtml+xml"),
    Map.entry("XML",            "application/xml"),
    Map.entry("YAML",           "application/x-yaml")
    // @formatter:on
  );

  final @NotNull String comment;
  final @NotNull String type;
  final @NotNull List<String> globPatterns;

  MimeTypeDescription(@NotNull FileType fileType) {
    comment = fileType.getDescription();
    type = getMimeType(fileType);
    globPatterns = OSAssociateFileTypesUtil.getMatchers(fileType).stream()
      .filter(ExtensionFileNameMatcher.class::isInstance)
      .map(FileNameMatcher::getPresentableString)
      .toList();
  }

  private static String getMimeType(FileType fileType) {
    if (fileType instanceof LanguageFileType) {
      String[] mimeTypes = ((LanguageFileType)fileType).getLanguage().getMimeTypes();
      if (mimeTypes.length > 0) return mimeTypes[0];
    }
    String typeName = fileType.getName();
    if (OS_MIME_TYPES.containsKey(typeName)) {
      return OS_MIME_TYPES.get(typeName);
    }
    String fromName = StringUtil.toLowerCase(typeName);
    fromName = TYPE_PREFIX + fromName.replace(" ", "-");
    return fromName;
  }

  @Override
  public int compareTo(@NotNull MimeTypeDescription o) {
    return type.compareTo(o.type);
  }
}
