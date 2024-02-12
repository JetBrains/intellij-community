// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator

class LoggerLookupElement(element: LookupElement, val loggerTypeName : String) : LookupElementDecorator<LookupElement>(element)