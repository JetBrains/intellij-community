// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions.associate.linux;

import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class MimeTypeDescription implements Comparable<MimeTypeDescription> {
  private final static String TYPE_PREFIX = "application/x-" + PlatformUtils.getPlatformPrefix() + "-";

  /**
   * See <a href="https://www.freedesktop.org/wiki/Specifications/shared-mime-info-spec">FreeDesktop Shared Mime Info</a>
   */
  private final static Map<String,String> OS_MIME_TYPES = new HashMap<>();
  static {
    // @formatter:off
    OS_MIME_TYPES.put("ASP",            "application/x-asp");
    OS_MIME_TYPES.put("CMakeLists.txt", "text/x-cmake");
    OS_MIME_TYPES.put("CSS",            "text/css");
    OS_MIME_TYPES.put("Cucumber",       "text/x-gherkin");
    OS_MIME_TYPES.put("DTD",            "application/xml-dtd");
    OS_MIME_TYPES.put("Go",             "text/x-go");
    OS_MIME_TYPES.put("Groovy",         "text/x-groovy");
    OS_MIME_TYPES.put("HTML",           "text/html");
    OS_MIME_TYPES.put("JAVA",           "text/x-java");
    OS_MIME_TYPES.put("JSON",           "application/json");
    OS_MIME_TYPES.put("JavaScript",     "application/javascript");
    OS_MIME_TYPES.put("Jupyter",        "application/x-ipynb+json");
    OS_MIME_TYPES.put("Log",            "text/x-log");
    OS_MIME_TYPES.put("Markdown",       "text/markdown");
    OS_MIME_TYPES.put("ObjectiveC",     "text/x-c++src");
    OS_MIME_TYPES.put("PHP",            "application/x-php");
    OS_MIME_TYPES.put("PLAIN_TEXT",     "text/plain");
    OS_MIME_TYPES.put("Python",         "text/x-python");
    OS_MIME_TYPES.put("QT UI file",     "application/x-designer");
    OS_MIME_TYPES.put("RNG Compact",    "application/relax-ng-compact-syntax");
    OS_MIME_TYPES.put("ReST",           "text/x-rst");
    OS_MIME_TYPES.put("Ruby",           "application/x-ruby");
    OS_MIME_TYPES.put("SCSS",           "text/x-scss");
    OS_MIME_TYPES.put("SVG",            "image/svg+xml");
    OS_MIME_TYPES.put("Sass",           "text/x-sass");
    OS_MIME_TYPES.put("XHTML",          "text/xhtml+xml");
    OS_MIME_TYPES.put("XML",            "application/xml");
    OS_MIME_TYPES.put("YAML",           "application/x-yaml");
    // @formatter:on
  }

  private final List<String> myGlobPatterns = new ArrayList<>();
  private final String myComment;
  private final String myType;
  private boolean myIsStandard;

  MimeTypeDescription(@NotNull FileType fileType) {
    myComment = fileType.getDescription();
    myType = getMimeType(fileType);
    for (FileNameMatcher matcher: FileTypeManager.getInstance().getAssociations(fileType)) {
      myGlobPatterns.add(matcher.getPresentableString());
    }
  }

  private String getMimeType(@NotNull FileType fileType) {
    if (fileType instanceof LanguageFileType) {
      String[] mimeTypes = ((LanguageFileType)fileType).getLanguage().getMimeTypes();
      if (mimeTypes.length > 0) return mimeTypes[0];
    }
    String typeName = fileType.getName();
    if (OS_MIME_TYPES.containsKey(typeName)) {
      myIsStandard = true;
      return OS_MIME_TYPES.get(typeName);
    }
    String fromName = StringUtil.toLowerCase(typeName);
    fromName = TYPE_PREFIX + fromName.replace(" ", "-");
    return fromName;
  }

  List<String> getGlobPatterns() {
    return myGlobPatterns;
  }

  String getComment() {
    return myComment;
  }

  String getType() {
    return myType;
  }

  @Override
  public int compareTo(@NotNull MimeTypeDescription o) {
    return myType.compareTo(o.myType);
  }

  boolean isStandard() {
    return myIsStandard;
  }
}
