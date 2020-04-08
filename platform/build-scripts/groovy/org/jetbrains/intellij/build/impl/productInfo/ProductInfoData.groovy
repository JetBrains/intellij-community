// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.productInfo

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * Describes format of JSON file containing meta-information about a product installation. Must be consistent with 'product-info.schema.json' file.
 */
@Immutable
@CompileStatic
class ProductInfoData {
  String name
  String version
  String versionSuffix
  String buildNumber
  String productCode
  String dataDirectoryName
  String svgIconPath
  List<ProductInfoLaunchData> launch = []
}