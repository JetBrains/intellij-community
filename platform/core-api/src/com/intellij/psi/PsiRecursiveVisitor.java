// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

/**
 * Please use this interface to mark recursive visitors.
 * This information can then be used by the runtime to find accidental performance issues, e.g. see assertions in
 * com.intellij.codeInspection.LocalInspectionTool#processFile and
 * com.intellij.codeInspection.InspectionEngine#createVisitorAndAcceptElements.
 */
public interface PsiRecursiveVisitor {
}
