/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefVisitor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * User: anna
 * Date: 28-Dec-2005
 */
public abstract class GlobalInspectionTool extends InspectionProfileEntry {
  @Nullable
  public RefGraphAnnotator getAnnotator(final RefManager refManager){
    return null;
  }

  public void runInspection(final AnalysisScope scope,
                            final InspectionManager manager,
                            final GlobalInspectionContext globalContext,
                            final ProblemDescriptionsProcessor problemDescriptionsProcessor,
                            final boolean filterSuppressed) {
    final HashMap<RefEntity, List<CommonProblemDescriptor>> holder = new HashMap<RefEntity, List<CommonProblemDescriptor>>();
    globalContext.getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (globalContext.isSuppressed(refEntity, getShortName())) return;
        CommonProblemDescriptor[] descriptors = checkElement(refEntity, scope, manager, globalContext);
        if (descriptors != null){
          List<CommonProblemDescriptor> problemDescriptors = holder.get(refEntity);
          if (problemDescriptors == null){
            problemDescriptors = new ArrayList<CommonProblemDescriptor>();
            holder.put(refEntity, problemDescriptors);
          }
          problemDescriptors.addAll(Arrays.asList(descriptors));
        }
      }
    });

    for (RefEntity refElement : holder.keySet()) {
      final List<CommonProblemDescriptor> descriptors = holder.get(refElement);
      problemDescriptionsProcessor.addProblemElement(refElement, descriptors.toArray(new CommonProblemDescriptor[descriptors.size()]));
    }
  }

  @Nullable
  public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope scope, InspectionManager manager, GlobalInspectionContext globalContext) {
    return null;
  }

  public boolean isGraphNeeded() {
    return false;
  }

  public boolean queryExternalUsagesRequests(final InspectionManager manager,
                                             final GlobalInspectionContext globalContext,
                                             final ProblemDescriptionsProcessor problemDescriptionsProcessor){
    return false;
  }

}
