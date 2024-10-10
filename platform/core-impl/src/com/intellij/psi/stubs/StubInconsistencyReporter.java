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
    DeliberateAdditionalCheckInCompletion, //Registry.is("ide.check.stub.text.consistency") is enabled
    DeliberateAdditionalCheckInIntentions, //Registry.is("ide.check.stub.text.consistency") is enabled
    WrongTypePsiInStubHelper,
    OffsetOutsideFileInJava,
    CheckAfterExceptionInJava,
    NoPsiMatchingASTinJava,

    ForTests,//is definitely not expected to actually appear in FUS
    Other //better use null
  }

  enum InconsistencyType {
    DifferentNumberOfPsiTrees, MismatchingPsiTree
  }

  /**
   * Sometimes stub inconsistency-related exception is thrown even when no inconsistency is found during the usual check
   *
   * @deprecated all related methods are deprecated
   */
  @Deprecated
  enum EnforcedInconsistencyType {
    PsiOfUnexpectedClass("psi_of_unexpected_class"), Other("other");
    private final String fusDescription;

    EnforcedInconsistencyType(String fusDescription) {
      this.fusDescription = fusDescription;
    }

    @SuppressWarnings("unused")
    public String getFusDescription() {
      return fusDescription;
    }
  }

  void reportStubInconsistencyBetweenPsiAndText(@NotNull Project project,
                                                @Nullable StubInconsistencyReporter.SourceOfCheck reason,
                                                @NotNull InconsistencyType type);

  /**
   * @deprecated Use {@link #reportStubInconsistencyBetweenPsiAndText(Project, SourceOfCheck, InconsistencyType)}
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  void reportEnforcedStubInconsistency(@NotNull Project project, @NotNull StubInconsistencyReporter.SourceOfCheck reason,
                                       @NotNull EnforcedInconsistencyType enforcedInconsistencyType);

  /**
   * @deprecated Use {@link #reportStubInconsistencyBetweenPsiAndText(Project, SourceOfCheck, InconsistencyType)}
   */
  @Deprecated
  void reportStubInconsistencyBetweenPsiAndText(@NotNull Project project, @NotNull StubInconsistencyReporter.SourceOfCheck reason,
                                                @NotNull InconsistencyType type,
                                                @Nullable EnforcedInconsistencyType enforcedInconsistencyType);

  void reportKotlinDescriptorNotFound(@Nullable Project project);

  void reportKotlinMissingClassName(@NotNull Project project,
                                    boolean foundInKotlinFullClassNameIndex,
                                    boolean foundInEverythingScope);

  /**
   * Use nulls for parameters of this type in plugins. These values are reserved for the platform
   */
  enum StubTreeAndIndexDoNotMatchSource {FileTreesPsiReconciliation, WrongPsiFileClassInNonPsiStub, ZeroStubIdList, StubPsiCheck}

  void reportStubTreeAndIndexDoNotMatch(@NotNull Project project, @NotNull StubTreeAndIndexDoNotMatchSource source);
}
