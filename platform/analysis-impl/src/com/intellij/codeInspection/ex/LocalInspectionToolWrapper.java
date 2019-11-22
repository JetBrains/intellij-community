// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author max
 */
public class LocalInspectionToolWrapper extends InspectionToolWrapper<LocalInspectionTool, LocalInspectionEP> {
  /** This should be used in tests primarily */
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
      Map<String, LocalInspectionEP> map = new THashMap<>();
      Application application = ApplicationManager.getApplication();
      LocalInspectionEP.LOCAL_INSPECTION.getPoint(application).addExtensionPointListener(
        new ExtensionPointListener<LocalInspectionEP>() {
          @Override
          public void extensionAdded(@NotNull LocalInspectionEP extension, @NotNull PluginDescriptor pluginDescriptor) {
            map.put(extension.getShortName(), extension);
          }

          @Override
          public void extensionRemoved(@NotNull LocalInspectionEP extension, @NotNull PluginDescriptor pluginDescriptor) {
            map.remove(extension.getShortName());
          }
        }, true, application);
      return map;
    }
  };

  @Nullable
  public static InspectionToolWrapper findTool2RunInBatch(@NotNull Project project, @Nullable PsiElement element, @NotNull String name) {
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
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
