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
package com.intellij.lang.java;

import com.intellij.codeInsight.javadoc.JavaDocExternalFilter;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.codeInsight.javadoc.JavaDocInfoGeneratorFactory;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public class FileDocumentationProvider extends AbstractDocumentationProvider {

  @Override
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    final JavaDocInfoGenerator javaDocInfoGenerator = JavaDocInfoGeneratorFactory.create(element.getProject(), element);
    return JavaDocExternalFilter.filterInternalDocInfo(javaDocInfoGenerator.generateFileInfo());
  }
}
