/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 7, 2002
 * Time: 1:11:08 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class BinopInstruction extends BranchingInstruction {
  private final String myOperationSign;
  private final Project myProject;

  public BinopInstruction(@NonNls String opSign, PsiElement psiAnchor, @NotNull Project project) {
    myProject = project;
    if (opSign != null && ("==".equals(opSign) || "!=".equals(opSign) || "instanceof".equals(opSign) || "+".equals(opSign))) {
      myOperationSign = opSign;
    }
    else {
      myOperationSign = null;
    }

    setPsiAnchor(psiAnchor);
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitBinop(this, runner, stateBefore);
  }

  public DfaValue getNonNullStringValue(final DfaValueFactory factory) {
    PsiElement anchor = getPsiAnchor();
    Project project = myProject;
    PsiClassType string = PsiType.getJavaLangString(PsiManager.getInstance(project), anchor == null ? GlobalSearchScope.allScope(project) : anchor.getResolveScope());
    return factory.getNotNullFactory().create(string);
  }

  public String toString() {
    return "BINOP " + myOperationSign;
  }

  public String getOperationSign() {
    return myOperationSign;
  }
}
