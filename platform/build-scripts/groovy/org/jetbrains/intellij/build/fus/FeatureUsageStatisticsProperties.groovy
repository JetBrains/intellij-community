// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.fus

import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class FeatureUsageStatisticsProperties {
  final String recorderId
  final String metadataProviderUri
}
