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

package com.intellij.codeInspection.inconsistentLanguageLevel;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.unnecessaryModuleDependency.UnnecessaryModuleDependencyInspection;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class InconsistentLanguageLevelInspection extends GlobalInspectionTool {
  @Override
  public boolean isGraphNeeded() {
    return false;
  }

  @Override
  public boolean isReadActionNeeded() {
    return false;
  }

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope scope,
                                                           @NotNull InspectionManager manager,
                                                           @NotNull GlobalInspectionContext globalContext,
                                                           @NotNull ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefModule) {
      Module module = ((RefModule)refEntity).getModule();
      if (module.isDisposed() || !scope.containsModule(module)) return null;
      LanguageLevel languageLevel = LanguageLevelUtil.getEffectiveLanguageLevel(module);
      for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
        if (!(entry instanceof ModuleOrderEntry)) continue;
        final Module dependantModule = ((ModuleOrderEntry)entry).getModule();
        if (dependantModule == null) continue;
        LanguageLevel dependantLanguageLevel = LanguageLevelUtil.getEffectiveLanguageLevel(dependantModule);
        if (languageLevel.compareTo(dependantLanguageLevel) < 0) {
          final CommonProblemDescriptor problemDescriptor = manager.createProblemDescriptor(
            JavaAnalysisBundle
              .message("module.0.with.language.level.1.depends.on.module.2.with.language.level.3", module.getName(), languageLevel,
                       dependantModule.getName(), dependantLanguageLevel),
            module,
            new UnnecessaryModuleDependencyInspection.RemoveModuleDependencyFix(dependantModule.getName()),
            (QuickFix)QuickFixFactory.getInstance().createShowModulePropertiesFix(module));
          return new CommonProblemDescriptor[] {problemDescriptor};
        }
      }
    }
    return null;
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.modularization.issues");
  }

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
    return "InconsistentLanguageLevel";
  }

}
