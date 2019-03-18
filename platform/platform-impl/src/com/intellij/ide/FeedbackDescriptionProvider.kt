// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.project.Project

/**
 * Allows plugins to add additional details, which we want to see pre-populated in the bug reports.
 */
interface FeedbackDescriptionProvider {

    /**
     * @return additional description details.
     */
    fun getDescription(project: Project?): String?
}
