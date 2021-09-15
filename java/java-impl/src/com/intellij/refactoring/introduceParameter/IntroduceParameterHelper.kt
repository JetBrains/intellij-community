// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceParameter

import com.intellij.psi.PsiParameter

fun onClickCallback(psiParameter: PsiParameter): () -> Unit {
  return {
    psiParameter.navigate(true)
  }
}
