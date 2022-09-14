// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.impl.BuildUtils
import org.jetbrains.jps.model.JpsProject
import java.nio.file.Files
import java.text.MessageFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Suppress("SpellCheckingInspection")
private val BUILD_DATE_PATTERN = DateTimeFormatter.ofPattern("uuuuMMddHHmm")

@VisibleForTesting
@Suppress("SpellCheckingInspection")
internal val MAJOR_RELEASE_DATE_PATTERN: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMMdd")

class ApplicationInfoPropertiesImpl(
  productProperties: ProductProperties,
  buildOptions: BuildOptions,
  override val appInfoXml: String,
) : ApplicationInfoProperties {
  override val majorVersion: String
  override val minorVersion: String
  override val microVersion: String
  override val patchVersion: String
  override val fullVersionFormat: String
  override val isEAP: Boolean
  override val versionSuffix: String?
  /**
   * The first number from 'minor' part of the version. This property is temporary added because some products specify composite number (like '1.3')
   * in 'minor version' attribute instead of using 'micro version' (i.e. set minor='1' micro='3').
   */
  override val minorVersionMainPart: String
  override val shortProductName: String
  override val productCode: String
  override val productName: String
  override val majorReleaseDate: String
  override val edition: String?
  override val motto: String?
  override val companyName: String
  override val shortCompanyName: String
  override val svgRelativePath: String?
  override val svgProductIcons: List<String>
  override val patchesUrl: String?

  constructor(project: JpsProject, productProperties: ProductProperties, buildOptions: BuildOptions) :
    this(productProperties = productProperties,
         buildOptions = buildOptions,
         appInfoXml = findApplicationInfoInSources(project, productProperties)
    )

  init {
    val root = readXmlAsModel(appInfoXml.reader())

    val versionTag = root.getChild("version")!!
    majorVersion = versionTag.getAttributeValue("major")!!
    minorVersion = versionTag.getAttributeValue("minor") ?: "0"
    microVersion = versionTag.getAttributeValue("micro") ?: "0"
    patchVersion = versionTag.getAttributeValue("patch") ?: "0"
    fullVersionFormat = versionTag.getAttributeValue("full") ?: "{0}.{1}"
    isEAP = versionTag.getAttributeValue("eap").toBoolean()
    versionSuffix = versionTag.getAttributeValue("suffix") ?: (if (isEAP) "EAP" else null)
    minorVersionMainPart = minorVersion.takeWhile { it != '.' }

    val namesTag = root.getChild("names")!!
    shortProductName = namesTag.getAttributeValue("product")!!
    val buildTag = root.getChild("build")!!
    val buildNumber = buildTag.getAttributeValue("number")!!
    val productCodeSeparator = buildNumber.indexOf('-')
    var productCode: String? = null
    if (productCodeSeparator != -1) {
      productCode = buildNumber.substring(0, productCodeSeparator)
    }
    if (productProperties.customProductCode != null) {
      productCode = productProperties.customProductCode
    }
    else if (productProperties.productCode != null && productCode == null) {
      productCode = productProperties.productCode
    }
    this.productCode = productCode!!
    val majorReleaseDate = buildTag.getAttributeValue("majorReleaseDate")
    check (isEAP || (majorReleaseDate != null && !majorReleaseDate.startsWith("__"))) {
      "majorReleaseDate may be omitted only for EAP"
    }
    this.majorReleaseDate = formatMajorReleaseDate(majorReleaseDate, buildOptions.buildDateInSeconds)
    productName = namesTag.getAttributeValue("fullname") ?: shortProductName
    edition = namesTag.getAttributeValue("edition")
    motto = namesTag.getAttributeValue("motto")

    val companyTag = root.getChild("company")!!
    companyName = companyTag.getAttributeValue("name")!!
    shortCompanyName = companyTag.getAttributeValue("shortName") ?: shortenCompanyName(companyName)

    val svgPath = root.getChild("icon")?.getAttributeValue("svg")
    svgRelativePath = if (isEAP) (root.getChild("icon-eap")?.getAttributeValue("svg") ?: svgPath) else svgPath
    svgProductIcons = sequenceOf(root.getChild("icon"), root.getChild("icon-eap"))
      .filterNotNull()
      .flatMap { listOf(it.getAttributeValue("svg"), it.getAttributeValue("svg-small")) }
      .filterNotNull()
      .toList()

    patchesUrl = root.getChild("update-urls")?.getAttributeValue("patches")
  }

  override val releaseVersionForLicensing: String
    get() = "${majorVersion}${minorVersionMainPart}00"
  override val upperCaseProductName: String
    get() = shortProductName.uppercase()
  override val fullVersion: String
    get() = MessageFormat.format(fullVersionFormat, majorVersion, minorVersion, microVersion, patchVersion)
  override val productNameWithEdition: String
    get() = if (edition == null) productName else "$productName $edition"


  override fun toString() = appInfoXml

  fun patch(buildContext: BuildContext): ApplicationInfoProperties {
    val artifactsServer = buildContext.proprietaryBuildTools.artifactsServer
    var builtinPluginsRepoUrl = ""
    if (artifactsServer != null && buildContext.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
      builtinPluginsRepoUrl = artifactsServer.urlToArtifact(buildContext, "$productCode-plugins/plugins.xml")!!
      check (!builtinPluginsRepoUrl.startsWith("http:")) {
        "Insecure artifact server: $builtinPluginsRepoUrl"
      }
    }
    val buildDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(buildContext.options.buildDateInSeconds), ZoneOffset.UTC)
    val patchedAppInfoXml = BuildUtils.replaceAll(
      text = appInfoXml,
      replacements = java.util.Map.of(
        "BUILD_NUMBER", "$productCode-${buildContext.buildNumber}",
        "BUILD_DATE", buildDate.format(BUILD_DATE_PATTERN),
        "BUILD", buildContext.buildNumber,
        "BUILTIN_PLUGINS_URL", builtinPluginsRepoUrl
      ),
      marker = "__"
    )
    return ApplicationInfoPropertiesImpl(buildContext.productProperties, buildContext.options, patchedAppInfoXml)
  }
}

//copy of ApplicationInfoImpl.shortenCompanyName
private fun shortenCompanyName(name: String) = name.removeSuffix(" s.r.o.").removeSuffix(" Inc.")

private fun findApplicationInfoInSources(project: JpsProject, productProperties: ProductProperties): String {
  val module = checkNotNull(project . modules . find { it.name == productProperties.applicationInfoModule }) {
    "Cannot find required '${productProperties.applicationInfoModule}' module"
  }
  val appInfoRelativePath = "idea/${productProperties.platformPrefix ?: ""}ApplicationInfo.xml"
  val appInfoFile = checkNotNull(module.sourceRoots.asSequence().map { it.path.resolve(appInfoRelativePath) }.firstOrNull { Files.exists(it) }) {
    "Cannot find $appInfoRelativePath in '$module.name' module"
  }
  return Files.readString(appInfoFile)
}

@VisibleForTesting
@JvmOverloads
fun formatMajorReleaseDate(majorReleaseDateRaw: String?, buildDateInSeconds: Long = (System.currentTimeMillis() / 1000)): String {
  if (majorReleaseDateRaw == null || majorReleaseDateRaw.startsWith("__")) {
    val buildDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(buildDateInSeconds), ZoneOffset.UTC)
    return buildDate.format(MAJOR_RELEASE_DATE_PATTERN)
  }
  else {
    try {
      MAJOR_RELEASE_DATE_PATTERN.parse(majorReleaseDateRaw)
      return majorReleaseDateRaw
    }
    catch (ignored: Exception) {
      return MAJOR_RELEASE_DATE_PATTERN.format(BUILD_DATE_PATTERN.parse(majorReleaseDateRaw))
    }
  }
}