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
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.lang.InspectionExtensionsFactory;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefElementImpl;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.profile.ProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.NotNull;

public class GlobalInspectionContextUtil {
  public static RefElement retrieveRefElement(@NotNull PsiElement element, @NotNull GlobalInspectionContext globalContext) {
    PsiFile elementFile = element.getContainingFile();
    RefElement refElement = globalContext.getRefManager().getReference(elementFile);
    if (refElement == null) {
      PsiElement context = InjectedLanguageManager.getInstance(elementFile.getProject()).getInjectionHost(elementFile);
      if (context != null) refElement = globalContext.getRefManager().getReference(context.getContainingFile());
    }
    return refElement;
  }


  public static boolean isToCheckMember(@NotNull RefElement owner, @NotNull InspectionProfileEntry tool, Tools tools, ProfileManager profileManager) {
    return isToCheckFile(((RefElementImpl)owner).getContainingFile(), tool, tools, profileManager) && !((RefElementImpl)owner).isSuppressed(tool.getShortName());
  }

  public static boolean isToCheckFile(PsiFile file, @NotNull InspectionProfileEntry tool, Tools tools, ProfileManager profileManager) {
    if (tools != null && file != null) {
      for (ScopeToolState state : tools.getTools()) {
        final NamedScope namedScope = state.getScope(file.getProject());
        if (namedScope == null || namedScope.getValue().contains(file, profileManager.getScopesManager())) {
          if (state.isEnabled()) {
            InspectionToolWrapper toolWrapper = state.getTool();
            if (toolWrapper.getTool() == tool) return true;
          }
          return false;
        }
      }
    }
    return false;
  }


  public static boolean canRunInspections(@NotNull Project project, final boolean online) {
    for (InspectionExtensionsFactory factory : Extensions.getExtensions(InspectionExtensionsFactory.EP_NAME)) {
      if (!factory.isProjectConfiguredToRunInspections(project, online)) {
        return false;
      }
    }
    return true;
  }
}
