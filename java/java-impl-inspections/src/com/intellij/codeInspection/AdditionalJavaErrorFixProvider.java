// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.AbstractJavaErrorFixProvider;
import com.intellij.codeInsight.daemon.impl.quickfix.VariableAccessFromInnerClassJava10Fix;
import com.intellij.codeInspection.streamMigration.SimplifyForEachInspection;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;

/**
 * Some quick-fixes not accessible from the java.analysis module are registered here.
 */
public final class AdditionalJavaErrorFixProvider extends AbstractJavaErrorFixProvider {
  public AdditionalJavaErrorFixProvider() {
    fix(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_LAMBDA, error -> new SimplifyForEachInspection.ForEachNonFinalFix(error.psi()));
    fix(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_LAMBDA, error -> new VariableAccessFromInnerClassJava10Fix(error.psi()));
    fix(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_GUARD, error -> new VariableAccessFromInnerClassJava10Fix(error.psi()));
  }
}
