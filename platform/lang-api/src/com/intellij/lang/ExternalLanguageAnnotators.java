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

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

public class ExternalLanguageAnnotators extends LanguageExtension<ExternalAnnotator>{

  private static final ExternalLanguageAnnotators INSTANCE = new ExternalLanguageAnnotators();

  private ExternalLanguageAnnotators() {
    super("com.intellij.externalAnnotator");
  }

  public static List<ExternalAnnotator> allForFile(Language language, final PsiFile file) {
    List<ExternalAnnotator> annotators = INSTANCE.allForLanguage(language);
    final ExternalAnnotatorsFilter[] filters = Extensions.getExtensions(ExternalAnnotatorsFilter.EXTENSION_POINT_NAME);
    return ContainerUtil.findAll(annotators, new Condition<ExternalAnnotator>() {
      @Override
      public boolean value(ExternalAnnotator annotator) {
        for (ExternalAnnotatorsFilter filter : filters) {
          if (filter.isProhibited(annotator, file)) {
            return false;
          }
        }
        return true;
      }
    });
  }
}