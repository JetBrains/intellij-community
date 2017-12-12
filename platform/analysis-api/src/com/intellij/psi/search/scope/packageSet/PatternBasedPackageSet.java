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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public abstract class PatternBasedPackageSet extends PackageSetBase {
  protected final Pattern myModulePattern;
  protected final Pattern myModuleGroupPattern;
  protected final String myModulePatternText;

  public PatternBasedPackageSet(@NonNls String modulePatternText) {
    myModulePatternText = modulePatternText;
    Pattern moduleGroupPattern = null;
    Pattern modulePattern = null;
    if (modulePatternText == null || modulePatternText.isEmpty()) {
      modulePattern = null;
    }
    else {
      if (modulePatternText.startsWith("group:")) {
        int index = modulePatternText.indexOf(':', 6);
        if (index == -1) index = modulePatternText.length();
        moduleGroupPattern = Pattern.compile(StringUtil.replace(escapeToRegexp(modulePatternText.substring(6, index)), "*", ".*"));
        if (index < modulePatternText.length() - 1) {
          modulePattern = Pattern.compile(StringUtil.replace(escapeToRegexp(modulePatternText.substring(index + 1)), "*", ".*"));
        }
      }
      else {
        modulePattern = Pattern.compile(StringUtil.replace(escapeToRegexp(modulePatternText), "*", ".*"));
      }
    }
    myModulePattern = modulePattern;
    myModuleGroupPattern = moduleGroupPattern;
  }

  public abstract String getPattern();

  public abstract boolean isOn(String oldQName);

  public String getModulePattern() {
    return myModulePatternText;
  }

  @NotNull
  private static String escapeToRegexp(@NotNull CharSequence text) {
    StringBuilder builder = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (c == ' ' || Character.isLetter(c) || Character.isDigit(c) || c == '_' || c == '*') {
        builder.append(c);
      }
      else {
        builder.append('\\').append(c);
      }
    }

    return builder.toString();
  }
}
