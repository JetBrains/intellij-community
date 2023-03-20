// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.impl.BuildUtils
import org.jetbrains.intellij.build.impl.logging.reportBuildProblem
import org.jetbrains.intellij.build.impl.readSnapshotBuildNumber
import org.jetbrains.jps.model.JpsProject
import java.nio.file.Files
import java.nio.file.Path
import java.text.MessageFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.io.path.bufferedReader

@Suppress("SpellCheckingInspection")
private val BUILD_DATE_PATTERN = DateTimeFormatter.ofPattern("uuuuMMddHHmm")

@VisibleForTesting
@Suppress("SpellCheckingInspection")
internal val MAJOR_RELEASE_DATE_PATTERN: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMMdd")

class ApplicationInfoPropertiesImpl: ApplicationInfoProperties {
  override lateinit var majorVersion: String
  override lateinit var minorVersion: String
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
  override lateinit var productCode: String
  override val productName: String
  override val majorReleaseDate: String
  override val edition: String?
  override val motto: String?
  override val companyName: String
  override val shortCompanyName: String
  override val svgRelativePath: String?
  override val svgProductIcons: List<String>
  override val patchesUrl: String?
  private lateinit var context: BuildContext

  constructor(context: BuildContext) : this(context.project, context.productProperties, context.options) {
    this.context = context
  }

  constructor(project: JpsProject,
              productProperties: ProductProperties,
              buildOptions: BuildOptions) {
    val root = findApplicationInfoInSources(project, productProperties)
      .bufferedReader()
      .use(::readXmlAsModel)

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
    this.majorReleaseDate = run {
      val majorReleaseDate = buildTag.getAttributeValue("majorReleaseDate")
      when {
        isEAP -> {
          val buildDate = Instant.ofEpochSecond(buildOptions.buildDateInSeconds)
          val expirationDate = buildDate.plus(30, ChronoUnit.DAYS)
          val now = Instant.ofEpochMilli(System.currentTimeMillis())
          if (expirationDate < now) {
            reportBuildProblem(
              "Supplied build date is $buildDate, " +
              "so expiration date is in the past, " +
              "distribution won't be able to start"
            )
          }
        }
        majorReleaseDate == null || majorReleaseDate.startsWith("__") -> {
          error("majorReleaseDate may be omitted only for EAP")
        }
      }
      formatMajorReleaseDate(majorReleaseDate, buildOptions.buildDateInSeconds)
    }
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
  override val appInfoXml by lazy {
    check(this::context.isInitialized) {
      "buildContext property is not initialized, please use different constructor"
    }
    val appInfoXmlPath = findApplicationInfoInSources(context.project, context.productProperties)
    val snapshotBuildNumber = readSnapshotBuildNumber(context.paths.communityHomeDirRoot)
    check("$majorVersion$minorVersion".removePrefix("20") == snapshotBuildNumber.takeWhile { it != '.' }) {
      "'major=$majorVersion' and 'minor=$minorVersion' attributes of '$appInfoXmlPath' don't match snapshot build number '$snapshotBuildNumber'"
    }
    val artifactsServer = context.proprietaryBuildTools.artifactsServer
    var builtinPluginsRepoUrl = ""
    if (artifactsServer != null && context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
      builtinPluginsRepoUrl = artifactsServer.urlToArtifact(context, "$productCode-plugins/plugins.xml")!!
      check(!builtinPluginsRepoUrl.startsWith("http:")) {
        "Insecure artifact server: $builtinPluginsRepoUrl"
      }
    }
    val buildDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(context.options.buildDateInSeconds), ZoneOffset.UTC)
    BuildUtils.replaceAll(
      text = Files.readString(appInfoXmlPath),
      replacements = mapOf(
        "BUILD_NUMBER" to "$productCode-${context.buildNumber}",
        "BUILD_DATE" to buildDate.format(BUILD_DATE_PATTERN),
        "BUILD" to context.buildNumber,
        "BUILTIN_PLUGINS_URL" to builtinPluginsRepoUrl
      ),
      marker = "__"
    )
  }
}

//copy of ApplicationInfoImpl.shortenCompanyName
private fun shortenCompanyName(name: String) = name.removeSuffix(" s.r.o.").removeSuffix(" Inc.")

private fun findApplicationInfoInSources(project: JpsProject, productProperties: ProductProperties): Path {
  val module = checkNotNull(project.modules.find { it.name == productProperties.applicationInfoModule }) {
    "Cannot find required '${productProperties.applicationInfoModule}' module"
  }
  val appInfoRelativePath = "idea/${productProperties.platformPrefix ?: ""}ApplicationInfo.xml"
  return checkNotNull(module.sourceRoots.asSequence().map { it.path.resolve(appInfoRelativePath) }.firstOrNull { Files.exists(it) }) {
    "Cannot find $appInfoRelativePath in '$module.name' module"
  }
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