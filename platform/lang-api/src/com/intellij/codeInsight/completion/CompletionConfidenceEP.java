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
package com.intellij.codeInsight.completion;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageExtensionPoint;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class CompletionConfidenceEP extends LanguageExtensionPoint<CompletionContributor> {
  private static final LanguageExtension<CompletionConfidence> INSTANCE = new LanguageExtension<CompletionConfidence>("com.intellij.completion.confidence");

  public static List<CompletionConfidence> forLanguage(@NotNull Language language) {
    return INSTANCE.forKey(language);
  }
}
