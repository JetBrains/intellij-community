// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project

/**
 * Allows plugins to add additional details, which we want to see pre-populated in the bug reports. The implementation of this interface
 * must be registered as an extension of 'com.intellij.feedbackDescriptionProvider' extension point.
 */
interface FeedbackDescriptionProvider {
  /**
   * Return additional details which should be appended to the description of a created issue. It's important to return `null` if your plugin
   * isn't relevant for the passed `project` to avoid polluting created reports with unrelated data.
   */
  fun getDescription(project: Project?, indicator: ProgressIndicator?): String?
}
