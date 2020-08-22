// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.util;

import com.intellij.openapi.util.NlsContext;
import org.jetbrains.annotations.Nls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@NlsContext(prefix = "inspection.message")
@Nls(capitalization = Nls.Capitalization.Sentence)
@Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD})
public @interface InspectionMessage {
}