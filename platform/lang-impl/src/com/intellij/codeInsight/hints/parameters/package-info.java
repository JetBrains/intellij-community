// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * This package contains experimental support for parameter hints from inlay providers other than
 * {@link com.intellij.codeInsight.hints.InlayParameterHintsProvider InlayParameterHintsProvider},
 * namely the possibility to share exclude lists between different types of providers.
 *
 * @see com.intellij.codeInsight.hints.InlayParameterHintsProvider#getBlackListDependencyLanguage()
 */
package com.intellij.codeInsight.hints.parameters;