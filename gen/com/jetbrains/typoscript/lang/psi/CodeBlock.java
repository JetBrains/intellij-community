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
  List<Copying> getCopyingList();

  @NotNull
  List<MultilineValueAssignment> getMultilineValueAssignmentList();

  @NotNull
  List<ObjectPath> getObjectPathList();

  @NotNull
  List<Unsetting> getUnsettingList();

  @NotNull
  List<ValueModification> getValueModificationList();

}
