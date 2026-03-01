// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.ui

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.java.JavaBundle
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.profile.codeInspection.ui.ErrorOptionsProvider

public class JavaErrorOptionsProvider : BeanConfigurable<DaemonCodeAnalyzerSettings>(
  DaemonCodeAnalyzerSettings.getInstance()), ErrorOptionsProvider {

  init {
    checkBox(JavaBundle.message("checkbox.suppress.with.suppresswarnings"), instance::isSuppressWarnings, instance::setSuppressWarnings)
  }
}