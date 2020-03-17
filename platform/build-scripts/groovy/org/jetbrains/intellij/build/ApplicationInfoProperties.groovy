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
  final String versionSuffix
  final String shortProductName
  /**
   * The first number from 'minor' part of the version. This property is temporary added because some products specify composite number (like '1.3')
   * in 'minor version' attribute instead of using 'micro version' (i.e. set minor='1' micro='3').
   */
  final String minorVersionMainPart
  String productCode
  final String productName
  final String edition
  final String motto
  final String companyName
  final String shortCompanyName
  final String svgRelativePath
  final boolean isEAP
  final List<String> svgProductIcons

  @SuppressWarnings(["GrUnresolvedAccess", "GroovyAssignabilityCheck"])
  ApplicationInfoProperties(String appInfoXmlPath) {
    def root = new XmlParser().parse(new File(appInfoXmlPath))
    def versionTag = root.version.first()
    majorVersion = versionTag.@major
    minorVersion = versionTag.@minor ?: "0"
    microVersion = versionTag.@micro ?: "0"
    patchVersion = versionTag.@patch ?: "0"
    fullVersionFormat = versionTag.@full ?: "{0}.{1}"
    isEAP = Boolean.parseBoolean(versionTag.@eap)
    versionSuffix = versionTag.@suffix ?: isEAP ? "EAP" : null

    shortProductName = root.names.first().@product
    String buildNumber = root.build.first().@number
    int productCodeSeparator = buildNumber.indexOf('-')
    if (productCodeSeparator != -1) {
      productCode = buildNumber.substring(0, productCodeSeparator)
    }
    productName = root.names.first().@fullname ?: shortProductName
    edition = root.names.first().@edition
    motto = root.names.first().@motto
    companyName = root.company.first().@name
    minorVersionMainPart = minorVersion.takeWhile { it != '.' }
    shortCompanyName = root.company.first().@shortName ?: shortenCompanyName(companyName)
    def svgPath = root.icon.first().@svg
    svgRelativePath = isEAP && !root."icon-eap".isEmpty() ? (root."icon-eap".first().@svg ?: svgPath) : svgPath

    svgProductIcons = (root.icon + root."icon-eap").collectMany { [it?.@"svg", it?.@"svg-small"] }.findAll { it != null }
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