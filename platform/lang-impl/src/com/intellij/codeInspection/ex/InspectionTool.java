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

/*
 * Author: max
 * Date: Oct 9, 2001
 * Time: 8:50:56 PM
 */

package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import org.jetbrains.annotations.NotNull;

public abstract class InspectionTool extends InspectionProfileEntry {
  protected GlobalInspectionContextImpl myContext;

  public abstract void runInspection(@NotNull AnalysisScope scope, @NotNull InspectionManager manager);


  public abstract boolean isGraphNeeded();

  @NotNull
  public abstract JobDescriptor[] getJobDescriptors(@NotNull GlobalInspectionContext globalInspectionContext);

  @Override
  public boolean isEnabledByDefault() {
    return getDefaultLevel() != HighlightDisplayLevel.DO_NOT_SHOW;
  }


  @Override
  public void cleanup() {
    if (myContext != null) {
      projectClosed(myContext.getProject());
    }
    myContext = null;
  }

  public boolean queryExternalUsagesRequests(InspectionManager manager) {
    return false;
  }

  public void initialize(@NotNull GlobalInspectionContext context) {
    myContext = (GlobalInspectionContextImpl)context;
    projectOpened(context.getProject());
  }
}
