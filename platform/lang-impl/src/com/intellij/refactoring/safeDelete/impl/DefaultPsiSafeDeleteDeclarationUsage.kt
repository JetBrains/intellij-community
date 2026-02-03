// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.refactoring.safeDelete.api.PsiSafeDeleteDeclarationUsage

class DefaultPsiSafeDeleteDeclarationUsage(usage : PsiUsage) : DefaultPsiSafeDeleteUsage(usage, true, null), PsiSafeDeleteDeclarationUsage