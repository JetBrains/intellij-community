// This is a generated file. Not intended for manual editing.
package com.jetbrains.typoscript.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CodeBlock extends TypoScriptCompositeElement {

  @NotNull
  List<Assignment> getAssignmentList();

  @NotNull
  List<CodeBlock> getCodeBlockList();

  @NotNull
  List<ConditionElement> getConditionElementList();

  @NotNull
  List<Copying> getCopyingList();

  @NotNull
  List<IncludeStatementElement> getIncludeStatementElementList();

  @NotNull
  List<MultilineValueAssignment> getMultilineValueAssignmentList();

  @NotNull
  ObjectPath getObjectPath();

  @NotNull
  List<Unsetting> getUnsettingList();

  @NotNull
  List<ValueModification> getValueModificationList();

}
