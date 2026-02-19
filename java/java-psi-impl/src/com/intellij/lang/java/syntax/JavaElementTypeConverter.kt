// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.syntax

import com.intellij.lang.java.JavaLanguage
import com.intellij.platform.syntax.psi.ElementTypeConverter
import com.intellij.platform.syntax.psi.ElementTypeConverters

fun getJavaElementTypeConverter(): ElementTypeConverter = ElementTypeConverters.getConverter(JavaLanguage.INSTANCE)