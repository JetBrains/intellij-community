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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 7, 2002
 * Time: 1:11:08 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaRelation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BinopInstruction extends BranchingInstruction {

  private final @NotNull DfaRelation myOperationSign;
  private final @NotNull Project myProject;

  public BinopInstruction(@Nullable DfaRelation opSign, @Nullable PsiElement psiAnchor, @NotNull Project project) {
    super(psiAnchor);
    myProject = project;
    myOperationSign = opSign == null ? DfaRelation.UNDEFINED : opSign;
  }

  public BinopInstruction(@Nullable DfaRelation opSign, @NotNull PsiElement psiAnchor) {
    this(opSign, psiAnchor, psiAnchor.getProject());
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DfaMemoryState stateBefore, @NotNull InstructionVisitor visitor) {
    return visitor.visitBinop(this, stateBefore);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public String toString() {
    return "BINOP " + myOperationSign;
  }

  @NotNull
  public DfaRelation getOperationSign() {
    return myOperationSign;
  }
}
