/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection;

/**
 * QuickFix based on {@link com.intellij.codeInspection.ProblemDescriptor ProblemDescriptor}
 *
 * Implement {@link com.intellij.openapi.util.Iconable Iconable} interface to
 * change icon in quick fix popup menu
 *
 * N.B. Please DO NOT store PSI elements inside the LocalQuickFix instance, to avoid holding too much PSI files during inspection.
 * Instead, use the {@link ProblemDescriptor#getPsiElement()}
 * in {@link QuickFix#applyFix(com.intellij.openapi.project.Project, CommonProblemDescriptor)}
 * to retrieve the PSI context the fix will work on.
 * See also {@link LocalQuickFixOnPsiElement} which uses {@link com.intellij.psi.SmartPsiElementPointer} instead of storing PSI elements.
 *
 * @author max
 * @see LocalQuickFixBase
 * @see com.intellij.codeInspection.ProblemDescriptor
 * @see com.intellij.openapi.util.Iconable
 */
public interface LocalQuickFix extends QuickFix<ProblemDescriptor> {
  LocalQuickFix[] EMPTY_ARRAY = new LocalQuickFix[0];
}
