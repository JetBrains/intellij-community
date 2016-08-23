/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
 * User: anna
 * Date: 03-Nov-2009
 */
package com.intellij.codeInspection.inconsistentLanguageLevel;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.unnecessaryModuleDependency.UnnecessaryModuleDependencyInspection;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.*;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class InconsistentLanguageLevelInspection extends GlobalInspectionTool {
  @Override
  public boolean isGraphNeeded() {
    return false;
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope,
                            @NotNull InspectionManager manager,
                            @NotNull GlobalInspectionContext globalContext,
                            @NotNull ProblemDescriptionsProcessor problemProcessor) {
    final Set<Module> modules = new THashSet<>();
    scope.accept(new PsiElementVisitor(){
      @Override
      public void visitElement(PsiElement element) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(element);
        if (module != null) {
          modules.add(module);
        }
      }
    });

    LanguageLevel projectLanguageLevel = LanguageLevelProjectExtension.getInstance(manager.getProject()).getLanguageLevel();
    for (Module module : modules) {
      LanguageLevel languageLevel = LanguageLevelModuleExtensionImpl.getInstance(module).getLanguageLevel();
      if (languageLevel == null) {
        languageLevel = projectLanguageLevel;
      }
      RefManager refManager = globalContext.getRefManager();
      final RefModule refModule = refManager.getRefModule(module);
      for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
        if (!(entry instanceof ModuleOrderEntry)) continue;
        final Module dependantModule = ((ModuleOrderEntry)entry).getModule();
        if (dependantModule == null) continue;
        LanguageLevel dependantLanguageLevel = LanguageLevelModuleExtensionImpl.getInstance(dependantModule).getLanguageLevel();
        if (dependantLanguageLevel == null) {
          dependantLanguageLevel = projectLanguageLevel;
        }
        if (languageLevel.compareTo(dependantLanguageLevel) < 0) {
          final CommonProblemDescriptor problemDescriptor = manager.createProblemDescriptor(
            "Inconsistent language level settings: module " + module.getName() + " with language level " + languageLevel +
            " depends on module " + dependantModule.getName() +" with language level " + dependantLanguageLevel,
            new UnnecessaryModuleDependencyInspection.RemoveModuleDependencyFix(module, dependantModule),
            (QuickFix)QuickFixFactory.getInstance().createShowModulePropertiesFix(module));
          problemProcessor.addProblemElement(refModule, problemDescriptor);
        }
      }
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.MODULARIZATION_GROUP_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return "Inconsistent language level settings";
  }

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
    return "InconsistentLanguageLevel";
  }

}
