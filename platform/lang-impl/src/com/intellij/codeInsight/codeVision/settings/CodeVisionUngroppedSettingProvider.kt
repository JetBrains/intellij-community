// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.codeVision.settings

/**
 * Uses to create dummy settings for [com.intellij.codeInsight.codeVision.CodeVisionProvider] if no [CodeVisionGroupSettingProvider] were presented
 */
internal class CodeVisionUngroppedSettingProvider(override val groupId: String) : CodeVisionGroupSettingProvider