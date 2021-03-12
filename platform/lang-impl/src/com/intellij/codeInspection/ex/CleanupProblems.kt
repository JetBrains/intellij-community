// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.psi.PsiFile

class CleanupProblems(val files: Set<PsiFile>, val problemDescriptors: List<ProblemDescriptor>, val isGlobalScope: Boolean)