/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.lang.manifest.header.impl;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.lang.manifest.ManifestBundle;
import org.jetbrains.lang.manifest.header.HeaderParser;
import org.jetbrains.lang.manifest.psi.Header;
import org.jetbrains.lang.manifest.psi.HeaderValue;
import org.jetbrains.lang.manifest.psi.HeaderValuePart;

public class ClassReferenceParser extends StandardHeaderParser {
  public static final HeaderParser INSTANCE = new ClassReferenceParser();

  @NotNull
  @Override
  public PsiReference[] getReferences(@NotNull HeaderValuePart headerValuePart) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(headerValuePart);
    JavaClassReferenceProvider provider;
    if (module != null) {
      provider = new JavaClassReferenceProvider() {
        @Override
        public GlobalSearchScope getScope(Project project) {
          return GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
        }
      };
    }
    else {
      provider = new JavaClassReferenceProvider();
    }
    return provider.getReferencesByElement(headerValuePart);
  }

  @Override
  public boolean annotate(@NotNull Header header, @NotNull AnnotationHolder holder) {
    HeaderValue value = header.getHeaderValue();
    if (!(value instanceof HeaderValuePart)) return false;

    PsiReference[] references = value.getReferences();
    if (references.length == 0) {
      holder.createErrorAnnotation(((HeaderValuePart)value).getHighlightingRange(), ManifestBundle.message("header.reference.invalid"));
      return true;
    }

    for (int i = 0; i < references.length; i++) {
      PsiReference reference = references[i];
      PsiElement element = reference.resolve();
      if (element == null) {
        TextRange range = reference.getRangeInElement().shiftRight(value.getTextOffset());
        holder.createErrorAnnotation(range, ManifestBundle.message("header.reference.unknown"));
        return true;
      }

      if (i == references.length - 1) {
        if (checkClass(reference, element, holder)) {
          return true;
        }
      }
    }

    return false;
  }

  protected boolean checkClass(@NotNull PsiReference reference, @NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof PsiClass && PsiMethodUtil.hasMainMethod((PsiClass)element)) {
      return false;
    }

    TextRange range = reference.getRangeInElement().shiftRight(reference.getElement().getTextOffset());
    holder.createErrorAnnotation(range, ManifestBundle.message("header.main.class.invalid"));
    return true;
  }
}
