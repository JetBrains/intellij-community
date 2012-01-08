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

package com.intellij.codeInsight.daemon;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 * @author Konstantin Bulenkov
 */
public class LineMarkerProviders extends LanguageExtension<LineMarkerProvider> {
  public static LineMarkerProviders INSTANCE = new LineMarkerProviders();
  @NonNls public static final String EP_NAME = "com.intellij.codeInsight.lineMarkerProvider";

  private LineMarkerProviders() {
    super(EP_NAME);
  }

  @NotNull
  @Override
  public List<LineMarkerProvider> allForLanguage(Language l) {
    //TODO[kb] make this for all Language Extensions
    List<LineMarkerProvider> providers = super.allForLanguage(l);
    if (l == Language.ANY) return providers;
    List<LineMarkerProvider> any = super.allForLanguage(Language.ANY);
    if (providers.isEmpty()) return any;
    if (any.isEmpty()) return providers;
    ArrayList<LineMarkerProvider> result = new ArrayList<LineMarkerProvider>(providers);
    result.addAll(any);
    return result;
  }
}
