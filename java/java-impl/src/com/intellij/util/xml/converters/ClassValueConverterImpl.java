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
package com.intellij.util.xml.converters;

import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.converters.values.ClassValueConverter;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ConvertContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataCache;
import org.jetbrains.annotations.NotNull;

/**
 * User: Sergey.Vasiliev
 */
public class ClassValueConverterImpl extends ClassValueConverter {
  private static final UserDataCache<JavaClassReferenceProvider, Project, Object> REFERENCE_PROVIDER = new UserDataCache<JavaClassReferenceProvider, Project, Object>("ClassValueConverterImpl") {
    @Override
    protected JavaClassReferenceProvider compute(Project project, Object p) {
      JavaClassReferenceProvider provider = new JavaClassReferenceProvider(project);
      provider.setSoft(true);
      provider.setAllowEmpty(true);
      return provider;
    }
  };

  @NotNull
  public PsiReference[] createReferences(final GenericDomValue genericDomValue, final PsiElement element, final ConvertContext context) {
    return REFERENCE_PROVIDER.get(element.getManager().getProject(), null).getReferencesByElement(element);
  }
}
