/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.xml.util;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class XmlStringUtil {
  @NonNls private static final String HTML_HEADER = "<html>";
  @NonNls private static final String BODY_HEADER = "<body>";
  @NonNls private static final String HTML_FOOTER = "</html>";
  @NonNls private static final String BODY_FOOTER = "</body>";

  private XmlStringUtil() {
  }

  public static String escapeString(@Nullable String str) {
    return escapeString(str, false);
  }

  public static String escapeString(@Nullable String str, final boolean escapeWhiteSpace) {
    return XmlTagUtilBase.escapeString(str, escapeWhiteSpace);
  }

  @NotNull
  public static String wrapInHtml(@NotNull CharSequence result) {
    return HTML_HEADER + result + HTML_FOOTER;
  }

  @NotNull
  public static String stripHtml(@NotNull String toolTip) {
    toolTip = StringUtil.trimStart(toolTip, HTML_HEADER);
    toolTip = StringUtil.trimStart(toolTip, BODY_HEADER);
    toolTip = StringUtil.trimEnd(toolTip, HTML_FOOTER);
    toolTip = StringUtil.trimEnd(toolTip, BODY_FOOTER);
    return toolTip;
  }
}