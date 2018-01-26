/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.JavaTokenType.*;

public class BinopInstruction extends BranchingInstruction {
  private static final TokenSet ourSignificantOperations = TokenSet.create(EQEQ, NE, LT, GT, LE, GE, INSTANCEOF_KEYWORD, PLUS, MINUS, AND, PERC);
  private final IElementType myOperationSign;
  private final Project myProject;

  public BinopInstruction(IElementType opSign, @Nullable PsiElement psiAnchor, @NotNull Project project) {
    super(psiAnchor);
    myProject = project;
    myOperationSign = ourSignificantOperations.contains(opSign) ? opSign : null;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitBinop(this, runner, stateBefore);
  }

  public DfaValue getNonNullStringValue(final DfaValueFactory factory) {
    PsiElement anchor = getPsiAnchor();
    Project project = myProject;
    PsiClassType string = PsiType.getJavaLangString(PsiManager.getInstance(project), anchor == null ? GlobalSearchScope.allScope(project) : anchor.getResolveScope());
    return factory.createTypeValue(string, Nullness.NOT_NULL);
  }

  public String toString() {
    return "BINOP " + myOperationSign;
  }

  public IElementType getOperationSign() {
    return myOperationSign;
  }
}
