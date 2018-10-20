// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.productInfo

import groovy.transform.CompileStatic
import groovy.transform.Immutable

@Immutable
@CompileStatic
class ProductInfoLaunchData {
  String os
  String launcherPath
  String javaExecutablePath
  String vmOptionsFilePath
  String startupWmClass
}