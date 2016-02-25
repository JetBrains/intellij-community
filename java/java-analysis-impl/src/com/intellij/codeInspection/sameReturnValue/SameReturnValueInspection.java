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
package com.intellij.codeInspection.sameReturnValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class SameReturnValueInspection extends GlobalJavaBatchInspectionTool {
  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity, @NotNull AnalysisScope scope, @NotNull InspectionManager manager, @NotNull GlobalInspectionContext globalContext,
                                                @NotNull ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

      if (refMethod.isConstructor()) return null;
      if (refMethod.hasSuperMethods()) return null;

      String returnValue = refMethod.getReturnValueIfSame();
      if (returnValue != null) {
        final String message;
        if (refMethod.getDerivedMethods().isEmpty()) {
          message = InspectionsBundle.message("inspection.same.return.value.problem.descriptor", "<code>" + returnValue + "</code>");
        } else if (refMethod.hasBody()) {
          message = InspectionsBundle.message("inspection.same.return.value.problem.descriptor1", "<code>" + returnValue + "</code>");
        } else {
          message = InspectionsBundle.message("inspection.same.return.value.problem.descriptor2", "<code>" + returnValue + "</code>");
        }

        final PsiModifierListOwner element = refMethod.getElement();
        if (element != null) {
          return new ProblemDescriptor[] {manager.createProblemDescriptor(element.getNavigationElement(), message, false, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
        }
      }
    }

    return null;
  }


  @Override
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager, @NotNull final GlobalJavaInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
    manager.iterate(new RefJavaVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        if (refEntity instanceof RefElement && processor.getDescriptions(refEntity) != null) {
          refEntity.accept(new RefJavaVisitor() {
            @Override public void visitMethod(@NotNull final RefMethod refMethod) {
              globalContext.enqueueDerivedMethodsProcessor(refMethod, new GlobalJavaInspectionContext.DerivedMethodsProcessor() {
                @Override
                public boolean process(PsiMethod derivedMethod) {
                  processor.ignoreElement(refMethod);
                  return false;
                }
              });
            }
          });
        }
      }
    });

    return false;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.same.return.value.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @NotNull
  public String getShortName() {
    return "SameReturnValue";
  }
}
