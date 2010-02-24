/*
 * Copyright 2010 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.LanguageFileType;

import java.util.Vector;

/**
 * Base class and extension point for code style settings shared between multiple languages
 * (blank lines, indent and braces, spaces).
 *
 * @author rvishnyakov
 */
public abstract class LanguageCodeStyleSettingsProvider {

  public final static int BLANK_LINE_SETTINGS = 1;
  public final static int INDENT_AND_BRACE_SETTINGS = 2;
  public final static int SPACE_SETTINGS = 3;

  public static final ExtensionPointName<LanguageCodeStyleSettingsProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.langCodeStyleSettingsProvider");

  public abstract LanguageFileType getLanguageFileType();

  public abstract String getBlankLinesCodeSample();

  public abstract String getIndentAndBracesCodeSample();

  public abstract String getOtherCodeSample();

  public static LanguageFileType[] getLanguageFileTypes() {
    Vector<LanguageFileType> langFileTypes = new Vector<LanguageFileType>();
    for (LanguageCodeStyleSettingsProvider provider : Extensions.getExtensions(EP_NAME)) {
      langFileTypes.add(provider.getLanguageFileType());
    }
    LanguageFileType fileType;
    return langFileTypes.toArray(new LanguageFileType[0]);
  }

  public static String getCodeSample(Language lang, int settingsType) {
    for (LanguageCodeStyleSettingsProvider provider : Extensions.getExtensions(EP_NAME)) {
      if (provider.getLanguageFileType().getLanguage().equals(lang)) {
        switch (settingsType) {
          case BLANK_LINE_SETTINGS:
            return provider.getBlankLinesCodeSample();
          case INDENT_AND_BRACE_SETTINGS:
            return provider.getIndentAndBracesCodeSample();
          default:
            return provider.getOtherCodeSample();
        }
      }
    }
    return null;
  }

  public static LanguageFileType getFileType(String langName) {
    for (LanguageCodeStyleSettingsProvider provider : Extensions.getExtensions(EP_NAME)) {
      if (langName.equals(provider.getLanguageFileType().getLanguage().getDisplayName())) {
        return provider.getLanguageFileType();
      }
    }
    return null;
  }
}
