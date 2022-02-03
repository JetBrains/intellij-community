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
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;

/**
 * @author anna
 */
public class ReplaceWithSubtypeUsageInfo extends FixableUsageInfo {
  public static final Logger LOG = Logger.getInstance(ReplaceWithSubtypeUsageInfo.class);
  private final PsiTypeElement myTypeElement;
  private final PsiClassType myTargetClassType;
  private final PsiType myOriginalType;
  private @Nls String myConflict;

  public ReplaceWithSubtypeUsageInfo(PsiTypeElement typeElement, PsiClassType classType, final PsiClass[] targetClasses) {
    super(typeElement);
    myTypeElement = typeElement;
    myTargetClassType = classType;
    myOriginalType = myTypeElement.getType();
    if (targetClasses.length > 1) {
      myConflict = JavaRefactoringBundle.message("inline.super.type.element.can.be.replaced",
                                                 typeElement.getText(),
                                                 StringUtil.join(targetClasses, psiClass -> psiClass.getQualifiedName(), ", "));
    }
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    if (myTypeElement.isValid()) {
      Project project = myTypeElement.getProject();
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      PsiElement replaced = myTypeElement.replace(elementFactory.createTypeElement(myTargetClassType));
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
    }
  }

  @Override
  public String getConflictMessage() {
    if (!TypeConversionUtil.isAssignable(myOriginalType, myTargetClassType)) {
      final String conflict = JavaRefactoringBundle.message("inline.super.no.substitution",
                                                            getElement().getText(),
                                                            myOriginalType.getPresentableText(),
                                                            myTargetClassType.getPresentableText());
      if (myConflict == null) {
        myConflict = conflict;
      } else {
        myConflict += "\n" + conflict;
      }
    }
    return myConflict;
  }
}
