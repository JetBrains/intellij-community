// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Nls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@NlsContext(prefix = "command.name")
@Nls(capitalization = Nls.Capitalization.Title)
@Target(ElementType.TYPE_USE)
public @interface CommandName {
}