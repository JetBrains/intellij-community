// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import java.nio.file.Files
import java.nio.file.Path
import java.text.MessageFormat

@CompileStatic
class ApplicationInfoProperties {
  final String majorVersion
  final String minorVersion
  final String microVersion
  final String patchVersion
  final String fullVersionFormat
  final boolean isEAP
  final String versionSuffix
  /**
   * The first number from 'minor' part of the version. This property is temporary added because some products specify composite number (like '1.3')
   * in 'minor version' attribute instead of using 'micro version' (i.e. set minor='1' micro='3').
   */
  final String minorVersionMainPart
  final String shortProductName
  String productCode
  final String productName
  final String majorReleaseDate
  final String edition
  final String motto
  final String companyName
  final String shortCompanyName
  final String svgRelativePath
  final List<String> svgProductIcons
  final String patchesUrl

  @SuppressWarnings(["GrUnresolvedAccess", "GroovyAssignabilityCheck"])
  @CompileStatic(TypeCheckingMode.SKIP)
  ApplicationInfoProperties(Path appInfoXmlPath) {
    def root = Files.newBufferedReader(appInfoXmlPath).withCloseable { new XmlParser().parse(it) }

    def versionTag = root.version.first()
    majorVersion = versionTag.@major
    minorVersion = versionTag.@minor ?: "0"
    microVersion = versionTag.@micro ?: "0"
    patchVersion = versionTag.@patch ?: "0"
    fullVersionFormat = versionTag.@full ?: "{0}.{1}"
    isEAP = Boolean.parseBoolean(versionTag.@eap)
    versionSuffix = versionTag.@suffix ?: isEAP ? "EAP" : null
    minorVersionMainPart = minorVersion.takeWhile { it != '.' }

    def namesTag = root.names.first()
    shortProductName = namesTag.@product
    String buildNumber = root.build.first().@number
    int productCodeSeparator = buildNumber.indexOf('-')
    if (productCodeSeparator != -1) {
      productCode = buildNumber.substring(0, productCodeSeparator)
    }
    majorReleaseDate = root.build.first().@majorReleaseDate
    productName = namesTag.@fullname ?: shortProductName
    edition = namesTag.@edition
    motto = namesTag.@motto

    def companyTag = root.company.first()
    companyName = companyTag.@name
    shortCompanyName = companyTag.@shortName ?: shortenCompanyName(companyName)

    def svgPath = root.icon[0]?.@svg
    svgRelativePath = isEAP ? (root."icon-eap"[0]?.@svg ?: svgPath) : svgPath
    svgProductIcons = (root.icon + root."icon-eap").collectMany { [it?.@"svg", it?.@"svg-small"] }.findAll { it != null }

    patchesUrl = root."update-urls"[0]?.@"patches"
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