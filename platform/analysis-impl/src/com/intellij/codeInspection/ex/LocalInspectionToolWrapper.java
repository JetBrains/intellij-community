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

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.HashMap;
import java.util.Map;

/**
 * @author max
 */
public class LocalInspectionToolWrapper extends InspectionToolWrapper<LocalInspectionTool, LocalInspectionEP> {
  /** This should be used in tests primarily */
  @TestOnly
  public LocalInspectionToolWrapper(@NotNull LocalInspectionTool tool) {
    super(tool, ourEPMap.getValue().get(tool.getShortName()));
  }

  public LocalInspectionToolWrapper(@NotNull LocalInspectionEP ep) {
    super(ep);
  }

  private LocalInspectionToolWrapper(@NotNull LocalInspectionToolWrapper other) {
    super(other);
  }

  @NotNull
  @Override
  public LocalInspectionToolWrapper createCopy() {
    return new LocalInspectionToolWrapper(this);
  }

  @Override
  @NotNull
  public JobDescriptor[] getJobDescriptors(@NotNull GlobalInspectionContext context) {
    return context.getStdJobDescriptors().LOCAL_ANALYSIS_ARRAY;
  }


  public boolean isUnfair() {
    return myEP == null ? getTool() instanceof UnfairLocalInspectionTool : myEP.unfair;
  }

  @Override
  public String getID() {
    return myEP == null ? getTool().getID() : myEP.id == null ? myEP.getShortName() : myEP.id;
  }

  @Nullable
  public String getAlternativeID() {
    return myEP == null ? getTool().getAlternativeID() : myEP.alternativeId;
  }

  public boolean runForWholeFile() {
    return myEP == null ? getTool().runForWholeFile() : myEP.runForWholeFile;
  }

  private static final NotNullLazyValue<Map<String, LocalInspectionEP>> ourEPMap = new NotNullLazyValue<Map<String, LocalInspectionEP>>() {
    @NotNull
    @Override
    protected Map<String, LocalInspectionEP> compute() {
      HashMap<String, LocalInspectionEP> map = new HashMap<String, LocalInspectionEP>();
      for (LocalInspectionEP ep : Extensions.getExtensions(LocalInspectionEP.LOCAL_INSPECTION)) {
        map.put(ep.getShortName(), ep);
      }
      return map;
    }
  };

  @Nullable
  public static InspectionToolWrapper findTool2RunInBatch(@NotNull Project project, @Nullable PsiElement element, @NotNull String name) {
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    final InspectionToolWrapper toolWrapper = element == null
                                           ? inspectionProfile.getInspectionTool(name, project)
                                           : inspectionProfile.getInspectionTool(name, element);
    return findTool2RunInBatch(project, element, inspectionProfile, toolWrapper);
  }

  @Nullable
  public static InspectionToolWrapper findTool2RunInBatch(@NotNull Project project,
                                                          @Nullable PsiElement element,
                                                          @NotNull InspectionProfile inspectionProfile,
                                                          @Nullable InspectionToolWrapper toolWrapper) {
    if (toolWrapper instanceof LocalInspectionToolWrapper && ((LocalInspectionToolWrapper)toolWrapper).isUnfair()) {
      LocalInspectionTool inspectionTool = ((LocalInspectionToolWrapper)toolWrapper).getTool();
      if (inspectionTool instanceof PairedUnfairLocalInspectionTool) {
        String oppositeShortName = ((PairedUnfairLocalInspectionTool)inspectionTool).getInspectionForBatchShortName();
        return element == null ? inspectionProfile.getInspectionTool(oppositeShortName, project)
                               : inspectionProfile.getInspectionTool(oppositeShortName, element);
      }
      return null;
    }
    return toolWrapper;
  }
}
