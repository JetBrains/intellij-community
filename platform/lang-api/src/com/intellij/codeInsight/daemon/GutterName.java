// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.util.NlsContext;
import org.jetbrains.annotations.Nls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@NlsContext(prefix = "gutter.name")
@Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
public @Nls(capitalization = Nls.Capitalization.Sentence) @interface GutterName {
}