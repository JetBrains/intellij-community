// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class HectorComponentFactoryImpl : HectorComponentFactory {
    override fun create(file: PsiFile): HectorComponent {
        return HectorComponentImpl(file)
    }
}