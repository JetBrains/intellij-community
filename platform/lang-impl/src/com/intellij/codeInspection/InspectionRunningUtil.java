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

import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 3/2/12
 */
public class InspectionRunningUtil {
  public static List<CommonProblemDescriptor> runInspectionOnFile(final PsiFile file,
                                                                  final LocalInspectionTool inspectionTool) {
    final InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(file.getProject());
    final GlobalInspectionContextImpl context = managerEx.createNewGlobalContext(false);
    final LocalInspectionToolWrapper tool = new LocalInspectionToolWrapper(inspectionTool);
    tool.initialize(context);
    ((RefManagerImpl)context.getRefManager()).inspectionReadActionStarted();
    try {
      tool.processFile(file, true, managerEx, true);
      return new ArrayList<CommonProblemDescriptor>(tool.getProblemDescriptors());
    }
    finally {
      ((RefManagerImpl)context.getRefManager()).inspectionReadActionFinished();
      context.cleanup(managerEx);
    }
  }
}
