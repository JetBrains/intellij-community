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
package org.jetbrains.lang.manifest.header.impl;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.lang.manifest.ManifestBundle;
import org.jetbrains.lang.manifest.header.HeaderParser;
import org.jetbrains.lang.manifest.psi.Header;
import org.jetbrains.lang.manifest.psi.HeaderValue;
import org.jetbrains.lang.manifest.psi.HeaderValuePart;

public class ClassReferenceParser extends StandardHeaderParser {
  public static final String MAIN_CLASS = "Main-Class";
  public static final String PREMAIN_CLASS = "Premain-Class";
  public static final String AGENT_CLASS = "Agent-Class";

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
    HeaderValuePart valuePart = (HeaderValuePart)value;

    String className = valuePart.getUnwrappedText();
    if (StringUtil.isEmptyOrSpaces(className)) {
      holder.createErrorAnnotation(valuePart.getHighlightingRange(), ManifestBundle.message("header.reference.invalid"));
      return true;
    }

    Project project = header.getProject();
    Module module = ModuleUtilCore.findModuleForPsiElement(header);
    GlobalSearchScope scope = module != null ? module.getModuleWithDependenciesAndLibrariesScope(false) : ProjectScope.getAllScope(project);
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(className, scope);
    if (aClass == null) {
      String message = JavaErrorMessages.message("error.cannot.resolve.class", className);
      Annotation anno = holder.createErrorAnnotation(valuePart.getHighlightingRange(), message);
      anno.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      return true;
    }

    return checkClass(valuePart, aClass, holder);
  }

  protected boolean checkClass(@NotNull HeaderValuePart valuePart, @NotNull PsiClass aClass, @NotNull AnnotationHolder holder) {
    String header = ((Header)valuePart.getParent()).getName();

    if (header.equals(MAIN_CLASS) && !PsiMethodUtil.hasMainMethod(aClass)) {
      holder.createErrorAnnotation(valuePart.getHighlightingRange(), ManifestBundle.message("header.main.class.invalid"));
      return true;
    }

    if (header.equals(PREMAIN_CLASS) && !hasInstrumenterMethod(aClass, "premain")) {
      holder.createErrorAnnotation(valuePart.getHighlightingRange(), ManifestBundle.message("header.pre-main.class.invalid"));
      return true;
    }

    if (header.equals(AGENT_CLASS) && !hasInstrumenterMethod(aClass, "agentmain")) {
      holder.createErrorAnnotation(valuePart.getHighlightingRange(), ManifestBundle.message("header.agent.class.invalid"));
      return true;
    }

    return false;
  }

  private static boolean hasInstrumenterMethod(PsiClass aClass, String methodName) {
    for (PsiMethod method : aClass.findMethodsByName(methodName, false)) {
      if (PsiType.VOID.equals(method.getReturnType()) &&
          method.hasModifierProperty(PsiModifier.PUBLIC) &&
          method.hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
    }

    return false;
  }
}