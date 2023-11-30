// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface StubInconsistencyReporter {

  static StubInconsistencyReporter getInstance() {
    return ApplicationManager.getApplication().getService(StubInconsistencyReporter.class);
  }

  enum SourceOfCheck {
    DeliberateAdditionalCheckInCompletion(
      "deliberate_additional_check_in_completion"), //Registry.is("ide.check.stub.text.consistency") is enabled
    DeliberateAdditionalCheckInIntentions(
      "deliberate_additional_check_in_intentions"), //Registry.is("ide.check.stub.text.consistency") is enabled
    WrongTypePsiInStubHelper("wrong_type_psi_in_stub_helper"),
    OffsetOutsideFileInJava("offset_outside_file_in_java"),
    CheckAfterExceptionInJava("check_after_exception_in_java"),
    NoPsiMatchingASTinJava("no_psi_matching_ast_in_java"),

    ForTests("for_tests"),//is definitely not expected to actually appear in FUS
    Other("other");

    private final String fusDescription;

    SourceOfCheck(String fusDescription) {
      this.fusDescription = fusDescription;
    }

    public String getFusDescription() {
      return fusDescription;
    }
  }

  enum InconsistencyType {
    DifferentNumberOfPsiTrees("different_number_of_psi_trees"), MismatchingPsiTree("mismatching_psi_tree");
    private final String fusDescription;

    InconsistencyType(String fusDescription) {
      this.fusDescription = fusDescription;
    }

    public String getFusDescription() {
      return fusDescription;
    }
  }

  /**
   * Sometimes stub inconsistency related exception is thrown even when no inconsistency is found during the usual check
   */
  enum EnforcedInconsistencyType {
    PsiOfUnexpectedClass("psi_of_unexpected_class"), Other("other");
    private final String fusDescription;

    EnforcedInconsistencyType(String fusDescription) {
      this.fusDescription = fusDescription;
    }

    public String getFusDescription() {
      return fusDescription;
    }
  }

  void reportEnforcedStubInconsistency(@NotNull Project project, @NotNull StubInconsistencyReporter.SourceOfCheck reason,
                                       @NotNull EnforcedInconsistencyType enforcedInconsistencyType);

  void reportStubInconsistency(@NotNull Project project, @NotNull StubInconsistencyReporter.SourceOfCheck reason,
                               @NotNull InconsistencyType type,
                               @Nullable EnforcedInconsistencyType enforcedInconsistencyType);
}
