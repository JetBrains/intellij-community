/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Irina.Chernushina on 2/18/2016.
 */
public interface CodeInsightProviders {
  @NotNull
  CompletionContributor getContributor();

  @NotNull
  Annotator getAnnotator();

  @NotNull
  DocumentationProvider getDocumentationProvider();

  @NotNull
  Convertor<String, PsiElement> getToPropertyResolver();
}
