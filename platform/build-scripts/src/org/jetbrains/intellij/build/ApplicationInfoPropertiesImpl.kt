// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xml.dom.readXmlAsModel
import org.jdom.Element
import org.jdom.Namespace
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.impl.BuildUtils
import org.jetbrains.intellij.build.impl.SnapshotBuildNumber
import org.jetbrains.intellij.build.impl.logging.reportBuildProblem
import org.jetbrains.jps.model.JpsProject
import java.nio.file.Files
import java.nio.file.Path
import java.text.MessageFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.io.path.name

private val BUILD_DATE_PATTERN = DateTimeFormatter.ofPattern("uuuuMMddHHmm")

@VisibleForTesting
internal val MAJOR_RELEASE_DATE_PATTERN: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuuMMdd")

@Suppress("KotlinRedundantDiagnosticSuppress", "UNNECESSARY_LATEINIT")
internal class ApplicationInfoPropertiesImpl(
  project: JpsProject,
  productProperties: ProductProperties,
  buildOptions: BuildOptions,
) : ApplicationInfoProperties {
  override lateinit var majorVersion: String
  override lateinit var minorVersion: String
  override val microVersion: String
  override val patchVersion: String
  override val fullVersionFormat: String
  override val isEAP: Boolean
  override val versionSuffix: String?
  override val minorVersionMainPart: String
  override val shortProductName: String
  override lateinit var productCode: String
  override val fullProductName: String
  override val majorReleaseDate: String
  override val edition: String?
  override val motto: String?
  override val companyName: String
  override val shortCompanyName: String
  override val svgRelativePath: String?
  override val svgProductIcons: List<String>
  override val launcherName: String

  override val releaseVersionForLicensing: String
    get() = "${majorVersion}${minorVersionMainPart}00"

  override val fullVersion: String
    get() = MessageFormat.format(fullVersionFormat, majorVersion, minorVersion, microVersion, patchVersion)

  override val productNameWithEdition: String
    get() = if (edition == null) fullProductName else "$fullProductName $edition"

  init {
    val root = readXmlAsModel(findApplicationInfoInSources(project = project, productProperties = productProperties))
    @Suppress("DEPRECATION")
    val applicationInfoOverrides = productProperties.applicationInfoOverride(project)
    val versionTag = root.getChild("version")!!
    majorVersion = applicationInfoOverrides?.majorVersion ?: versionTag.getAttributeValue("major")!!
    minorVersion = applicationInfoOverrides?.minorVersion ?: versionTag.getAttributeValue("minor") ?: "0"
    microVersion = applicationInfoOverrides?.microVersion ?: versionTag.getAttributeValue("micro") ?: "0"
    patchVersion = applicationInfoOverrides?.patchVersion ?: versionTag.getAttributeValue("patch") ?: "0"
    fullVersionFormat = applicationInfoOverrides?.fullVersionFormat ?: versionTag.getAttributeValue("full") ?: "{0}.{1}"
    isEAP = (System.getProperty(BuildOptions.INTELLIJ_BUILD_OVERRIDE_APPLICATION_VERSION_IS_EAP)
             ?: applicationInfoOverrides?.eap
             ?: versionTag.getAttributeValue("eap")).toBoolean()
    versionSuffix = (System.getProperty(BuildOptions.INTELLIJ_BUILD_OVERRIDE_APPLICATION_VERSION_SUFFIX)
                     ?: applicationInfoOverrides?.versionSuffix
                     ?: versionTag.getAttributeValue("suffix")) ?: (if (isEAP) "EAP" else null)
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
    @Suppress("DEPRECATION")
    if (productProperties.customProductCode != null) {
      productCode = productProperties.customProductCode
    }
    else if (productProperties.productCode != null && productCode == null) {
      productCode = productProperties.productCode
    }
    this.productCode = productCode!!
    this.majorReleaseDate = run {
      val majorReleaseDate = (System.getProperty(BuildOptions.INTELLIJ_BUILD_OVERRIDE_APPLICATION_VERSION_MAJOR_RELEASE_DATE)
                             ?: applicationInfoOverrides?.majorReleaseDate
                             ?: buildTag.getAttributeValue("majorReleaseDate"))?.takeIf { it.isNotEmpty() }
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
      formatMajorReleaseDate(majorReleaseDateRaw = majorReleaseDate, buildDateInSeconds = buildOptions.buildDateInSeconds)
    }
    fullProductName = applicationInfoOverrides?.fullProductName ?: namesTag.getAttributeValue("fullname") ?: shortProductName
    edition = applicationInfoOverrides?.editionName ?: namesTag.getAttributeValue("edition")
    motto = applicationInfoOverrides?.motto ?: namesTag.getAttributeValue("motto")
    launcherName = namesTag.getAttributeValue("script")!!
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
  }
}

internal fun computeAppInfoXml(context: BuildContext, appInfo: ApplicationInfoProperties): String {
  val appInfoXmlPath = findApplicationInfoInSources(context.project, context.productProperties)
  val snapshotBuildNumber = SnapshotBuildNumber.VALUE.takeWhile { it != '.' }
  check("${appInfo.majorVersion}${appInfo.minorVersion}".removePrefix("20").take(snapshotBuildNumber.count()) == snapshotBuildNumber) {
    "'major=${appInfo.majorVersion}' and 'minor=${appInfo.minorVersion}' attributes of '$appInfoXmlPath' don't match snapshot build number '$snapshotBuildNumber'"
  }

  val artifactServer = context.proprietaryBuildTools.artifactsServer
  var builtinPluginsRepoUrl = ""
  if (artifactServer != null && context.productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
    builtinPluginsRepoUrl = artifactServer.urlToArtifact(context, "${context.nonBundledPlugins.name}/plugins.xml")!!
    check(!builtinPluginsRepoUrl.startsWith("http:")) {
      "Insecure artifact server: $builtinPluginsRepoUrl"
    }
  }

  val buildDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(context.options.buildDateInSeconds), ZoneOffset.UTC)
  var patchedAppInfo = BuildUtils.replaceAll(
    text = Files.readString(appInfoXmlPath),
    replacements = mapOf(
      "BUILD_NUMBER" to "${appInfo.productCode}-${context.buildNumber}",
      "BUILD_DATE" to buildDate.format(BUILD_DATE_PATTERN),
      "BUILD" to context.buildNumber,
      "BUILTIN_PLUGINS_URL" to builtinPluginsRepoUrl
    ),
    marker = "__"
  )

  val isEapOverride = System.getProperty(BuildOptions.INTELLIJ_BUILD_OVERRIDE_APPLICATION_VERSION_IS_EAP)
  val suffixOverride = System.getProperty(BuildOptions.INTELLIJ_BUILD_OVERRIDE_APPLICATION_VERSION_SUFFIX)

  @Suppress("DEPRECATION")
  val appInfoOverride = context.productProperties.applicationInfoOverride(context.project)
  if (isEapOverride != null || suffixOverride != null || appInfoOverride != null) {
    patchedAppInfo = withAppInfoOverride(
      originalPatchedAppInfo = patchedAppInfo,
      isEapOverride = isEapOverride,
      suffixOverride = suffixOverride,
      appInfoXmlPath = appInfoXmlPath,
      appInfoOverride = appInfoOverride,
    )
  }
  return patchedAppInfo
}

private fun withAppInfoOverride(originalPatchedAppInfo: String,
                                isEapOverride: String?,
                                suffixOverride: String?,
                                appInfoXmlPath: Path,
                                @Suppress("DEPRECATION") appInfoOverride: ProductProperties.ApplicationInfoOverrides?): String {
  val element = JDOMUtil.load(originalPatchedAppInfo)

  @Suppress("HttpUrlsUsage")
  val namespace = Namespace.getNamespace("http://jetbrains.org/intellij/schema/application-info")

  fun replaceAttribute(element: Element, tag: String, override: String?) {
    if (override != null) {
      element.setAttribute(tag, override)
    }
    else {
      element.removeAttribute(tag)
    }
  }

  if (appInfoOverride != null) {
    val names = element.getChildren("names", namespace).singleOrNull()
                ?: error("Could not find or more than one child element 'names' under root of '$appInfoXmlPath'")

    val version = element.getChildren("version", namespace).singleOrNull()
                  ?: error("Could not find or more than one child element 'version' under root of '$appInfoXmlPath'")

    val build = element.getChildren("build", namespace).singleOrNull()
                ?: error("Could not find or more than one child element 'build' under root of '$appInfoXmlPath'")

    names.setAttribute("fullname", appInfoOverride.fullProductName)
    replaceAttribute(names, "edition", appInfoOverride.editionName)
    replaceAttribute(names, "motto", appInfoOverride.motto)

    replaceAttribute(version, "eap", appInfoOverride.eap)
    replaceAttribute(version, "major", appInfoOverride.majorVersion)
    replaceAttribute(version, "minor", appInfoOverride.minorVersion)
    replaceAttribute(version, "micro", appInfoOverride.microVersion)
    replaceAttribute(version, "patch", appInfoOverride.patchVersion)
    replaceAttribute(version, "full", appInfoOverride.fullVersionFormat)
    replaceAttribute(version, "suffix", appInfoOverride.versionSuffix)

    replaceAttribute(build, "majorReleaseDate", appInfoOverride.majorReleaseDate)
  }

  if (isEapOverride != null || suffixOverride != null) {
    val version = element.getChildren("version", namespace).singleOrNull()
                  ?: error("Could not find child element 'version' under root of '$appInfoXmlPath'")
    replaceAttribute(version, "eap", isEapOverride)
    replaceAttribute(version, "suffix", suffixOverride)
  }

  return JDOMUtil.write(element)
}

//copy of ApplicationInfoImpl.shortenCompanyName
private fun shortenCompanyName(name: String) = name.removeSuffix(" s.r.o.").removeSuffix(" Inc.")

fun findApplicationInfoInSources(project: JpsProject, productProperties: ProductProperties): Path {
  val module = checkNotNull(project.findModuleByName(productProperties.applicationInfoModule)) {
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
    catch (_: Exception) {
      return MAJOR_RELEASE_DATE_PATTERN.format(BUILD_DATE_PATTERN.parse(majorReleaseDateRaw))
    }
  }
}
