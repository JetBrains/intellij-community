package org.jetbrains.intellij.build.dev

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Namespace
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.applyApplicationInfoOverrides
import org.jetbrains.intellij.build.impl.BuildUtils
import org.jetbrains.intellij.build.impl.LayoutPatcher
import org.jetbrains.intellij.build.impl.PatchOverwriteMode
import java.nio.file.Files
import java.nio.file.Path

internal const val CWM_FRONTEND_CUSTOMIZATION_MODULE: String = "intellij.frontend.split.customization"
internal const val CWM_CLIENT_APPLICATION_INFO_PATH: String = "idea/JetBrainsClientApplicationInfo.xml"
internal const val CWM_PRODUCT_APPLICATION_INFO_MODULE: String = "intellij.idea.ultimate.customization"
internal const val CWM_PRODUCT_APPLICATION_INFO_PATH: String = "idea/ApplicationInfo.xml"
internal const val CWM_BUILD_NUMBER_MODULE: String = "cwm.frontend.build.number"
internal const val CWM_BUILD_NUMBER_PATH: String = "build.txt"
internal const val CWM_BRANDING_MODULE: String = "cwm.frontend.branding"
internal const val CWM_REMOTE_DEV_ICONS_MODULE: String = "intellij.remoteDev.icons"

/** Identifies the production CWM frontend callback without exposing its plugin layout module to the generator. */
@ApiStatus.Internal
interface DevPluginCwmFrontendLayoutCallbackOwner : DevPluginLayoutAssetOwner

private val CWM_ICON_PATCHES = listOf(
  "product_16_frontend.svg" to "JetBrainsClient_16.svg",
  "product_frontend.svg" to "JetBrainsClient_64.svg",
  "product_16_frontend_EAP.svg" to "JetBrainsClient_16_EAP.svg",
  "product_frontend_EAP.svg" to "JetBrainsClient_64_EAP.svg",
)

internal fun cwmFrontendCallbackSourceKeys(): List<Pair<String, String>> {
  return listOf(
    CWM_FRONTEND_CUSTOMIZATION_MODULE to CWM_CLIENT_APPLICATION_INFO_PATH,
    CWM_PRODUCT_APPLICATION_INFO_MODULE to CWM_PRODUCT_APPLICATION_INFO_PATH,
    CWM_BUILD_NUMBER_MODULE to CWM_BUILD_NUMBER_PATH,
  ) + CWM_ICON_PATCHES.map { (source, _) -> CWM_BRANDING_MODULE to source }
}

internal fun cwmFrontendCallbackOutputKeys(): List<Pair<String, String>> {
  return CWM_ICON_PATCHES.map { (_, destination) -> CWM_REMOTE_DEV_ICONS_MODULE to destination } +
         (CWM_FRONTEND_CUSTOMIZATION_MODULE to CWM_CLIENT_APPLICATION_INFO_PATH)
}

internal class DevPluginCwmFrontendLayoutCallbackRuntime : DevPluginSpecialLayoutCallbackRuntime {
  override fun create(callback: DevPluginLibraryLayoutCallback): LayoutPatcher {
    require(callback.kind == CWM_FRONTEND_CALLBACK_KIND && callback.sources.map { it.moduleName to it.path } == cwmFrontendCallbackSourceKeys() &&
            callback.sources.all { it.input != null } && callback.libraries.isEmpty() && callback.cwmFrontendOptions != null) {
      "The CWM frontend callback has stale declared inputs"
    }
    return { moduleOutputPatcher, _, context ->
      for ((source, destination) in CWM_ICON_PATCHES) {
        val sourceFile = requiredSource(context, CWM_BRANDING_MODULE, source)
        moduleOutputPatcher.patchModuleOutput(CWM_REMOTE_DEV_ICONS_MODULE, destination, Files.readAllBytes(sourceFile), overwrite = true)
      }

      val clientApplicationInfo = requiredSource(context, CWM_FRONTEND_CUSTOMIZATION_MODULE, CWM_CLIENT_APPLICATION_INFO_PATH)
      val productApplicationInfo = requiredSource(context, CWM_PRODUCT_APPLICATION_INFO_MODULE, CWM_PRODUCT_APPLICATION_INFO_PATH)
      val buildNumberFile = requiredSource(context, CWM_BUILD_NUMBER_MODULE, CWM_BUILD_NUMBER_PATH)
      val options = requireNotNull(callback.cwmFrontendOptions)
      moduleOutputPatcher.patchModuleOutput(
        moduleName = CWM_FRONTEND_CUSTOMIZATION_MODULE,
        path = CWM_CLIENT_APPLICATION_INFO_PATH,
        content = prepareCwmClientApplicationInfo(clientApplicationInfo, productApplicationInfo, buildNumberFile, options),
        overwrite = PatchOverwriteMode.TRUE,
      )
    }
  }
}

private fun requiredSource(context: BuildContext, moduleName: String, path: String): Path {
  return requireNotNull(context.findFileInModuleSources(moduleName, path)) { "Missing declared CWM frontend input '$moduleName/$path'" }
}

@Suppress("DEPRECATION")
internal fun prepareCwmClientApplicationInfo(
  clientFile: Path,
  productFile: Path,
  buildNumberFile: Path,
  options: DevPluginCwmFrontendCallbackOptions,
): String {
  val buildNumber = Files.readString(buildNumberFile).trim()
  require(buildNumber.isNotEmpty()) { "The CWM frontend build number is empty" }
  val replaced = BuildUtils.replaceAll(
    text = Files.readString(clientFile),
    replacements = mapOf(
      "BUILD_NUMBER" to "JBC-$buildNumber",
      "BUILD" to buildNumber,
      "BUILTIN_PLUGINS_URL" to "",
    ),
    marker = "__",
  )
  return applyApplicationInfoOverrides(
    originalPatchedAppInfo = replaced,
    isEapOverride = options.isEapOverride,
    suffixOverride = options.versionSuffixOverride,
    appInfoXmlPath = clientFile,
    appInfoOverride = loadProductApplicationInfoOverrides(productFile),
    branchName = options.branchName.takeIf { options.nightlyBuild || buildNumber.count { character -> character == '.' } <= 1 },
  )
}

@Suppress("DEPRECATION")
private fun loadProductApplicationInfoOverrides(file: Path): ProductProperties.ApplicationInfoOverrides {
  val root = JDOMUtil.load(file)

  @Suppress("HttpUrlsUsage")
  val namespace = Namespace.getNamespace("http://jetbrains.org/intellij/schema/application-info")
  val names = root.getChildren("names", namespace).singleOrNull() ?: error("The product application info has no unique names element: $file")
  val version = root.getChildren("version", namespace).singleOrNull() ?: error("The product application info has no unique version element: $file")
  val build = root.getChildren("build", namespace).singleOrNull() ?: error("The product application info has no unique build element: $file")
  return ProductProperties.ApplicationInfoOverrides(
    fullProductName = names.getAttributeValue("fullname") ?: names.getAttributeValue("product")
                      ?: error("The product application info has no product name: $file"),
    editionName = null,
    motto = names.getAttributeValue("motto"),
    eap = version.getAttributeValue("eap"),
    majorVersion = version.getAttributeValue("major"),
    minorVersion = version.getAttributeValue("minor"),
    microVersion = version.getAttributeValue("micro"),
    patchVersion = version.getAttributeValue("patch"),
    fullVersionFormat = version.getAttributeValue("full"),
    versionSuffix = version.getAttributeValue("suffix"),
    majorReleaseDate = build.getAttributeValue("majorReleaseDate"),
  )
}
