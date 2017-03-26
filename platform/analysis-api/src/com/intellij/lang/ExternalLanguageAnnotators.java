/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExternalLanguageAnnotators extends LanguageExtension<ExternalAnnotator> {
  public static final ExtensionPointName<LanguageExtensionPoint<ExternalAnnotator>> EP_NAME = ExtensionPointName.create("com.intellij.externalAnnotator");

  public static final ExternalLanguageAnnotators INSTANCE = new ExternalLanguageAnnotators();

  private ExternalLanguageAnnotators() {
    super(EP_NAME.getName());
  }

  @NotNull
  public static List<ExternalAnnotator> allForFile(@NotNull Language language, @NotNull final PsiFile file) {
    List<ExternalAnnotator> annotators = INSTANCE.allForLanguage(language);
    ExternalAnnotatorsFilter[] filters = Extensions.getExtensions(ExternalAnnotatorsFilter.EXTENSION_POINT_NAME);
    return ContainerUtil.findAll(annotators, annotator -> {
      for (ExternalAnnotatorsFilter filter : filters) {
        if (filter.isProhibited(annotator, file)) {
          return false;
        }
      }
      return true;
    });
  }
}