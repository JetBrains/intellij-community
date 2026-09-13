// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.customization.backend.welcomeScreen

import com.intellij.platform.ide.nonModalWelcomeScreen.backend.WelcomeScreenNewFileFeatureBackend

internal class NewJavaFileFeatureBackend: WelcomeScreenNewFileFeatureBackend() {
  override val featureKey: String = "New.Java.File"

  override val languageId: String = "JAVA"
}