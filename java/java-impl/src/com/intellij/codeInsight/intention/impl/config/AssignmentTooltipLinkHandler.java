/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles tooltip links in format {@code #assignment/escaped_full_tooltip_text}.
 * On a click comparison table opens.
 */
public class AssignmentTooltipLinkHandler extends TooltipLinkHandler {
  @Nullable
  @Override
  @NlsSafe
  public String getDescription(@NotNull String refSuffix, @NotNull Editor editor) {
    return StringUtil.unescapeXmlEntities(refSuffix);
  }

  @NotNull
  @Override
  public String getDescriptionTitle(@NotNull String refSuffix, @NotNull Editor editor) {
    return JavaBundle.message("inspection.message.full.description");
  }
}
