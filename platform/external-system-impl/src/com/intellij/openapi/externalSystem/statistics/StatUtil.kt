// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.internal.statistic.beans.ConvertUsagesUtil
import com.intellij.openapi.externalSystem.model.ProjectSystemId

fun escapeSystemId(systemId: ProjectSystemId): String = ConvertUsagesUtil.escapeDescriptorName(systemId.id.toLowerCase())