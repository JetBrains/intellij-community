/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericValue;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * User: Sergey.Vasiliev
 */
abstract public class DomElementsInspection extends LocalInspectionTool {


  @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
    if (isAcceptable(file)) {
        return findProblems(file, manager, isOnTheFly);
    }

    return super.checkFile(file, manager, isOnTheFly);
  }

  @Nullable
  public PsiElementVisitor buildVisitor(final ProblemsHolder holder, final boolean isOnTheFly) {
    return null;
  }

  abstract protected boolean isAcceptable(final PsiFile file);
  abstract protected ProblemDescriptor[] findProblems(final PsiFile file, InspectionManager manager, final boolean onTheFly);


}
