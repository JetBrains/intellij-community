// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.text.Strings
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.impl.BuildUtils
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Files
import java.text.MessageFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@CompileStatic
final class ApplicationInfoProperties {
  @SuppressWarnings('SpellCheckingInspection')
  private static final DateTimeFormatter BUILD_DATE_PATTERN = DateTimeFormatter.ofPattern("uuuuMMddHHmm")
  @VisibleForTesting
  @SuppressWarnings('SpellCheckingInspection')
  static final DateTimeFormatter MAJOR_RELEASE_DATE_PATTERN = DateTimeFormatter.ofPattern('uuuuMMdd')
  private final String appInfoXml
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
  final String productCode
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
  private ApplicationInfoProperties(ProductProperties productProperties, BuildOptions buildOptions, String appInfoXml, BuildMessages messages) {
    this.appInfoXml = appInfoXml
    Node root = loadXml(appInfoXml)

    Node versionTag = root["version"].first()
    majorVersion = versionTag.@major
    minorVersion = versionTag.@minor ?: "0"
    microVersion = versionTag.@micro ?: "0"
    patchVersion = versionTag.@patch ?: "0"
    fullVersionFormat = versionTag.@full ?: "{0}.{1}"
    isEAP = Boolean.parseBoolean(versionTag.@eap)
    versionSuffix = versionTag.@suffix ?: isEAP ? "EAP" : null
    minorVersionMainPart = minorVersion.takeWhile { it != '.' }

    def namesTag = root["names"].first()
    shortProductName = namesTag.@product
    String buildNumber = root["build"].first().@number
    int productCodeSeparator = buildNumber.indexOf('-')
    String productCode = null
    if (productCodeSeparator != -1) {
      productCode = buildNumber.substring(0, productCodeSeparator)
    }
    if (productProperties.customProductCode != null) {
      productCode = productProperties.customProductCode
    }
    else if (productProperties.productCode != null && productCode == null) {
      productCode = productProperties.productCode
    }
    this.productCode = productCode
    def majorReleaseDate = root["build"].first().@majorReleaseDate
    if (!isEAP && (majorReleaseDate == null || majorReleaseDate.startsWith('__'))) {
      messages.error("majorReleaseDate may be omitted only for EAP")
    }
    this.majorReleaseDate = formatMajorReleaseDate(majorReleaseDate, buildOptions.buildDateInSeconds)
    productName = namesTag.@fullname ?: shortProductName
    edition = namesTag.@edition
    motto = namesTag.@motto

    def companyTag = root["company"].first()
    companyName = companyTag.@name
    shortCompanyName = companyTag.@shortName ?: shortenCompanyName(companyName)

    def svgPath = getFirst(root["icon"])?.@svg
    svgRelativePath = isEAP ? (getFirst(root["icon-eap"])?.@svg ?: svgPath) : svgPath
    svgProductIcons = collectAllIcons(root)

    patchesUrl = getFirst(root["update-urls"])?.@"patches"
  }

  /** this code is extracted to a method to work around Groovy compiler bug (https://issues.apache.org/jira/projects/GROOVY/issues/GROOVY-10459) */
  private static Node getFirst(NodeList children) {
    if (children == null || children.isEmpty()) return null
    return children[0] as Node
  }

  @SuppressWarnings('GrUnresolvedAccess')
  @CompileDynamic
  private static List<String> collectAllIcons(Node root) {
    (root.icon + root."icon-eap").collectMany { [it?.@"svg", it?.@"svg-small"] }.findAll { it != null }
  }

  /** this code is extracted to a method to work around Groovy compiler bug (https://issues.apache.org/jira/projects/GROOVY/issues/GROOVY-10457) */
  private static Node loadXml(String appInfoXml) {
    new StringReader(appInfoXml).withCloseable { new XmlParser().parse(it) }
  }

  String getAppInfoXml() {
    return appInfoXml
  }

  @VisibleForTesting
  static String formatMajorReleaseDate(String majorReleaseDateRaw, long buildDateInSeconds = System.currentTimeSeconds()) {
    if (majorReleaseDateRaw == null || majorReleaseDateRaw.startsWith('__')) {
      def buildDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(buildDateInSeconds), ZoneOffset.UTC)
      return buildDate.format(MAJOR_RELEASE_DATE_PATTERN)
    }
    else {
      try {
        MAJOR_RELEASE_DATE_PATTERN.parse(majorReleaseDateRaw)
        return majorReleaseDateRaw
      }
      catch (Exception ignored) {
        return MAJOR_RELEASE_DATE_PATTERN.format(BUILD_DATE_PATTERN.parse(majorReleaseDateRaw))
      }
    }
  }

  ApplicationInfoProperties(JpsProject project, ProductProperties productProperties, BuildOptions buildOptions, BuildMessages messages) {
    this(productProperties, buildOptions, findApplicationInfoInSources(project, productProperties, messages), messages)
  }

  String getUpperCaseProductName() { shortProductName.toUpperCase() }

  String getFullVersion() {
    MessageFormat.format(fullVersionFormat, majorVersion, minorVersion, microVersion, patchVersion)
  }

  String getProductNameWithEdition() {
    edition != null ? productName + ' ' + edition : productName
  }

  @Override
  String toString() {
    return appInfoXml
  }

  @NotNull
  ApplicationInfoProperties patch(BuildContext buildContext) {
    ArtifactsServer artifactsServer = buildContext.proprietaryBuildTools.artifactsServer
    String builtinPluginsRepoUrl = ""
    if (artifactsServer != null && buildContext.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
      builtinPluginsRepoUrl = artifactsServer.urlToArtifact(buildContext, "$productCode-plugins/plugins.xml")
      if (builtinPluginsRepoUrl.startsWith("http:")) {
        buildContext.messages.error("Insecure artifact server: " + builtinPluginsRepoUrl)
      }
    }
    def buildDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(buildContext.options.buildDateInSeconds), ZoneOffset.UTC)
    def patchedAppInfoXml = BuildUtils.replaceAll(appInfoXml, Map.<String, String>of(
      "BUILD_NUMBER", productCode + "-" + buildContext.buildNumber,
      "BUILD_DATE", buildDate.format(BUILD_DATE_PATTERN),
      "BUILD", buildContext.buildNumber,
      "BUILTIN_PLUGINS_URL", builtinPluginsRepoUrl ?: ""
    ), "__")
    return new ApplicationInfoProperties(buildContext.productProperties, buildContext.options, patchedAppInfoXml, buildContext.messages)
  }

  //copy of ApplicationInfoImpl.shortenCompanyName
  private static String shortenCompanyName(String name) {
    return Strings.trimEnd(Strings.trimEnd(name, " s.r.o."), " Inc.")
  }

  private static @NotNull String findApplicationInfoInSources(JpsProject project, ProductProperties productProperties, BuildMessages messages) {
    JpsModule module = project.modules.find { it.name == productProperties.applicationInfoModule }
    if (module == null) {
      messages.error("Cannot find required '${productProperties.applicationInfoModule}' module")
    }
    def appInfoRelativePath = "idea/${productProperties.platformPrefix ?: ""}ApplicationInfo.xml"
    def appInfoFile = module.sourceRoots.collect { new File(it.file, appInfoRelativePath) }.find { it.exists() }
    if (appInfoFile == null) {
      messages.error("Cannot find $appInfoRelativePath in '$module.name' module")
      return null
    }
    return Files.readString(appInfoFile.toPath())
  }
}