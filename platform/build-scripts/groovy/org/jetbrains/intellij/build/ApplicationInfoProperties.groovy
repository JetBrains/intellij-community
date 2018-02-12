/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build

import com.intellij.openapi.util.text.StringUtil

import java.text.MessageFormat

/**
 * @author nik
 */
class ApplicationInfoProperties {
  final String majorVersion
  final String minorVersion
  final String microVersion
  final String patchVersion
  final String fullVersionFormat
  final String shortProductName
  /**
   * The first number from 'minor' part of the version. This property is temporary added because some products specify composite number (like '1.3')
   * in 'minor version' attribute instead of using 'micro version' (i.e. set minor='1' micro='3').
   */
  final String minorVersionMainPart
  final String productName
  final String edition
  final String companyName
  final String shortCompanyName
  final boolean isEAP

  @SuppressWarnings(["GrUnresolvedAccess", "GroovyAssignabilityCheck"])
  ApplicationInfoProperties(String appInfoXmlPath) {
    def root = new XmlParser().parse(new File(appInfoXmlPath))
    majorVersion = root.version.first().@major
    minorVersion = root.version.first().@minor ?: "0"
    microVersion = root.version.first().@micro ?: "0"
    patchVersion = root.version.first().@patch ?: "0"
    fullVersionFormat = root.version.first().@full ?: "{0}.{1}"
    shortProductName = root.names.first().@product
    productName = root.names.first().@fullname ?: shortProductName
    edition = root.names.first().@edition
    companyName = root.company.first().@name
    minorVersionMainPart = minorVersion.takeWhile { it != '.' }
    isEAP = Boolean.parseBoolean(root.version.first().@eap)
    shortCompanyName = root.company.first().@shortName ?: shortenCompanyName(companyName)
  }

  String getUpperCaseProductName() { shortProductName.toUpperCase() }

  String getFullVersion() {
    MessageFormat.format(fullVersionFormat, majorVersion, minorVersion, microVersion, patchVersion)
  }

  String getProductNameWithEdition() {
    edition != null ? productName + ' ' + edition : productName
  }

  //copy of ApplicationInfoImpl.shortenCompanyName
  private static String shortenCompanyName(String name) {
    return StringUtil.trimEnd(StringUtil.trimEnd(name, " s.r.o."), " Inc.")
  }
}