/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 3/2/12
 */
public class InspectionRunningUtil {
  public static List<CommonProblemDescriptor> runInspectionOnFile(@NotNull PsiFile file,
                                                                  @NotNull LocalInspectionTool inspectionTool) {
    return runInspectionOnFile(file, new LocalInspectionToolWrapper(inspectionTool));
  }

  @NotNull
  public static List<CommonProblemDescriptor> runInspectionOnFile(@NotNull PsiFile file, @NotNull InspectionToolWrapper tool) {
    final InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(file.getProject());
    final GlobalInspectionContextImpl context = managerEx.createNewGlobalContext(false);
    tool.initialize(context);
    RefManagerImpl refManager = (RefManagerImpl)context.getRefManager();
    refManager.inspectionReadActionStarted();
    try {
      if (tool instanceof LocalInspectionToolWrapper) {
        LocalInspectionTool localTool = ((LocalInspectionToolWrapper)tool).getTool();
        List<ProblemDescriptor> descriptors =
          InspectionEngine.inspect(Collections.singletonList(localTool), file, managerEx, false, false, new DaemonProgressIndicator());
        return new ArrayList<CommonProblemDescriptor>(descriptors);
      }
      if (tool instanceof GlobalInspectionToolWrapper) {
        final GlobalInspectionTool globalTool = ((GlobalInspectionToolWrapper)tool).getTool();
        if (globalTool instanceof GlobalSimpleInspectionTool) {
          GlobalSimpleInspectionTool simpleTool = (GlobalSimpleInspectionTool)globalTool;
          ProblemsHolder problemsHolder = new ProblemsHolder(managerEx, file, false);
          simpleTool.checkFile(file, managerEx, problemsHolder, context, tool);
          return new ArrayList<CommonProblemDescriptor>(tool.getProblemDescriptors());
        }
        RefElement fileRef = refManager.getReference(file);
        CommonProblemDescriptor[] descriptors = globalTool.checkElement(fileRef, new AnalysisScope(file), managerEx, context);
        if (descriptors != null) {
          return Arrays.asList(descriptors);
        }
      }
      return Collections.emptyList();
    }
    finally {
      refManager.inspectionReadActionFinished();
      tool.cleanup();
      context.cleanup(managerEx);
    }
  }
}
