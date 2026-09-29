// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo

import com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PLATFORM_LOADER_JAR
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.impl.PlatformJarNames.PLATFORM_CORE_NIO_FS
import org.jetbrains.intellij.build.impl.SnapshotBuildNumber
import org.jetbrains.intellij.build.impl.generateVmOptions
import org.jetbrains.intellij.build.impl.getBundledPluginModules
import org.jetbrains.intellij.build.impl.hasIcnsForFrontendMacApp
import org.jetbrains.intellij.build.impl.ideaPropertiesFatalErrorNotification
import org.jetbrains.intellij.build.impl.ideaPropertiesSettingsDir
import org.jetbrains.intellij.build.impl.osVmOptions
import org.jetbrains.intellij.build.impl.stdioMcpRunner.STDIO_MCP_RUNNER_BOOT_CLASS_PATH_JAR_NAMES
import org.jetbrains.intellij.build.impl.stdioMcpRunner.STDIO_MCP_RUNNER_COMMAND
import org.jetbrains.intellij.build.impl.stdioMcpRunner.STDIO_MCP_RUNNER_MAIN_CLASS
import org.jetbrains.intellij.build.impl.stdioMcpRunner.stdioMcpRunnerVmOptionsFilePath
import org.jetbrains.intellij.build.loadDevDistributionApplicationInfo
import org.jetbrains.intellij.build.productLayout.COMPOSE_PLUGIN_MODULE
import org.jetbrains.intellij.build.productLayout.JNA_NATIVE_DIR
import org.jetbrains.intellij.build.productLayout.JNA_PLUGIN_MODULE
import org.jetbrains.intellij.build.productLayout.PTY4J_NATIVE_DIR
import org.jetbrains.intellij.build.productLayout.PTY4J_PLUGIN_MODULE
import org.jetbrains.intellij.build.productLayout.SKIKO_NATIVE_DIR
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.nio.file.Files

/**
 * The launch facts of one product: what `build.txt`, `bin/idea.properties`, the vmoptions file and
 * `bin/product-info.json` of a distribution state, for every OS and architecture.
 *
 * It needs no build context. [computeProductLaunchModel] derives it. The dev distribution plan generator writes the
 * model of each split product as JSON, and the tool `product-files` renders the four files of one OS and
 * architecture from it. They must be the files that the production writers write, byte for byte.
 *
 * The model states the facts of the product code. It states no fact of the application info and no build number. The
 * tool reads the application info sources and `build.txt` for the names, the version, the suffix, the icon, the vendor,
 * the release date and the Linux window class.
 */
@ApiStatus.Internal
@Serializable
data class ProductLaunchModel(
  @JvmField val productCode: String,
  @JvmField val envVarBaseName: String,
  @JvmField val dataDirectoryName: String,
  @JvmField val minRequiredJavaVersion: Int,
  @JvmField val customProperties: List<ProductLaunchProperty> = emptyList(),
  /** The flavors of the product. A launch that bundles a runtime lists `jbr17` before them when [jbr17] is set. */
  @JvmField val flavors: List<String> = emptyList(),
  @JvmField val jbr17: Boolean = false,
  @JvmField val baseFileName: String,
  /** A language server keeps `bin` at the root of the macOS distribution and adds no `64` to its file names. */
  @JvmField val languageServer: Boolean = false,
  @JvmField val launch: ProductLaunchCommand,
  @JvmField val customCommands: List<ProductLaunchCustomCommand> = emptyList(),
  /** The lines of the vmoptions file, keyed by [OsFamily.osName]. */
  @JvmField val vmOptions: Map<String, List<String>>,
  @JvmField val ideaProperties: ProductLaunchIdeaProperties,
)

@ApiStatus.Internal
@Serializable
data class ProductLaunchProperty(@JvmField val key: String, @JvmField val value: String)

/** The main launch of a product. */
@ApiStatus.Internal
@Serializable
data class ProductLaunchCommand(
  @JvmField val mainClass: String,
  @JvmField val bootClassPathJarNames: List<String>,
  @JvmField val jvmArguments: ProductJvmArguments,
  @JvmField val stdioRedirectArg: String? = null,
)

/**
 * One custom command of the launch, such as `thinClient`.
 *
 * Its JVM arguments are [jvmArguments] rendered for the OS and architecture, then [macJvmArguments] on macOS, then
 * [extraJvmArguments].
 */
@ApiStatus.Internal
@Serializable
data class ProductLaunchCustomCommand(
  @JvmField val commands: List<String>,
  /** The vmoptions file of the command, keyed by [OsFamily.osName]. A command with no entry names none. */
  @JvmField val vmOptionsFilePath: Map<String, String> = emptyMap(),
  @JvmField val bootClassPathJarNames: List<String> = emptyList(),
  @JvmField val jvmArguments: ProductJvmArguments? = null,
  /** Renders [jvmArguments] the way Qodana starts: without the multi-routing file system. */
  @JvmField val qodana: Boolean = false,
  @JvmField val macJvmArguments: List<String> = emptyList(),
  @JvmField val extraJvmArguments: List<String> = emptyList(),
  @JvmField val mainClass: String? = null,
  @JvmField val envVarBaseName: String? = null,
  @JvmField val dataDirectoryName: String? = null,
)

/**
 * The facts `BuildContext.getAdditionalJvmArguments` reads. [renderAdditionalJvmArguments] adds the OS and the
 * architecture, and the `--add-opens` lines of the OS.
 */
@ApiStatus.Internal
@Serializable
data class ProductJvmArguments(
  @JvmField val xBootClassPathJarNames: List<String> = emptyList(),
  /** Whether the multi-routing file system is on for the product, which puts [PLATFORM_CORE_NIO_FS] on the boot class path. */
  @JvmField val multiRoutingFileSystem: Boolean = false,
  /** The file name of the CDS archive, when the product enables CDS. */
  @JvmField val cdsArchiveFileName: String? = null,
  @JvmField val classLoader: String? = null,
  @JvmField val vendorName: String,
  @JvmField val pathsSelector: String,
  /** The JNA native tree relative to the IDE home, when the product bundles the JNA plugin. See [JNA_NATIVE_DIR]. */
  @JvmField val jnaNativeDir: String? = null,
  /** The pty4j native tree relative to the IDE home, when the product bundles the pty4j plugin. See [PTY4J_NATIVE_DIR]. */
  @JvmField val pty4jNativeDir: String? = null,
  /** The Skiko native tree relative to the IDE home, when the product bundles the Skiko plugin. See [SKIKO_NATIVE_DIR]. */
  @JvmField val skikoNativeDir: String? = null,
  @JvmField val runtimeModuleRepository: Boolean = false,
  /** The root module of the modular loader, when the product starts through it. */
  @JvmField val rootModule: String? = null,
  @JvmField val productMode: String? = null,
  @JvmField val platformPrefix: String? = null,
  @JvmField val additional: List<String> = emptyList(),
  @JvmField val splash: Boolean = false,
  @JvmField val nativeAccess: Boolean = false,
)

/**
 * The parts of `bin/idea.properties`: the base file, then each of [additions] after a newline, with
 * `@@settings_dir@@` replaced by [settingsDir], then [suffix].
 */
@ApiStatus.Internal
@Serializable
data class ProductLaunchIdeaProperties(
  /** The base file is `language-server/build/idea.properties`, not `community/bin/idea.properties`. */
  @JvmField val languageServerBase: Boolean = false,
  @JvmField val additions: List<String> = emptyList(),
  @JvmField val settingsDir: String,
  @JvmField val suffix: String = "",
)

/** One product as a build context sees it, which [computeProductLaunchModel] reads instead of the context. */
@ApiStatus.Internal
class ProductLaunchInputs(
  @JvmField val properties: ProductProperties,
  @JvmField val applicationInfo: ApplicationInfoProperties,
  @JvmField val buildNumber: String,
  @JvmField val bundledPluginModules: Collection<String>,
  /** `BuildContext.useModularLoader`. */
  @JvmField val useModularLoader: Boolean,
  /** `BuildContext.generateRuntimeModuleRepository`. */
  @JvmField val generateRuntimeModuleRepository: Boolean,
  @JvmField val bootClassPathJarNames: List<String>,
  /** Loads the application info of another product, for [ProductProperties.getAdditionalContextDependentIdeJvmArguments]. */
  @JvmField val applicationInfoOf: (ProductProperties) -> ApplicationInfoProperties,
) {
  val isLanguageServer: Boolean
    get() = properties.platformPrefix == LANGUAGE_SERVER_PLATFORM_PREFIX

  val systemSelector: String by lazy { properties.getSystemSelector(applicationInfo, buildNumber) }

  val mainClass: String
    get() = if (useModularLoader) MODULAR_LOADER_MAIN_CLASS else properties.mainClassName
}

/** `BuildContext.isLanguageServer`. */
private const val LANGUAGE_SERVER_PLATFORM_PREFIX = "IntelliJServer"

/** `BuildContext.ideMainClassName` of a product that starts through the modular loader. */
private const val MODULAR_LOADER_MAIN_CLASS = "com.intellij.platform.runtime.loader.IntellijLoader"

private val EMBEDDED_FRONTEND_COMMANDS = listOf("thinClient", "thinClient-headless", "installFrontendPlugins")

private const val FRONTEND_ENV_VAR_BASE_NAME = "JETBRAINS_CLIENT"

private const val FRONTEND_MAC_ICON_JVM_ARGUMENT = $$"-Dapple.awt.application.icon=$APP_PACKAGE/Contents/Resources/frontend.icns"

/**
 * Derives the launch model of [product] from what a build context of it would read.
 *
 * [embeddedFrontend] is the frontend the product embeds, or `null`. [minRequiredJavaVersion] is the language level of
 * the project, and [bundledRuntimeBuild] the `runtimeBuild` of the dependencies.
 */
@ApiStatus.Internal
fun computeProductLaunchModel(
  product: ProductLaunchInputs,
  embeddedFrontend: ProductLaunchInputs?,
  minRequiredJavaVersion: Int,
  bundledRuntimeBuild: String,
  pluginRepositoryVmOptions: List<String> = emptyList(),
): ProductLaunchModel {
  val properties = product.properties
  val applicationInfo = product.applicationInfo
  val bundledRuntimeVersion = bundledRuntimeBuild.takeWhile { it != '.' }.toInt()
  val jvmArguments = productJvmArguments(product, bundledRuntimeVersion)
  val frontendMacIcon = hasIcnsForFrontendMacApp(properties.imagesDirectoryPath, applicationInfo.isEAP)

  fun frontendCommand(commands: List<String>, frontend: ProductLaunchInputs, extraJvmArguments: List<String>): ProductLaunchCustomCommand {
    return ProductLaunchCustomCommand(
      commands = commands,
      vmOptionsFilePath = OsFamily.ALL.associate { os ->
        os.osName to vmOptionsFilePath(os, frontend.properties.baseFileName, frontend.isLanguageServer, hostLanguageServer = product.isLanguageServer)
      },
      bootClassPathJarNames = frontend.bootClassPathJarNames,
      jvmArguments = productJvmArguments(frontend, bundledRuntimeVersion),
      macJvmArguments = if (frontendMacIcon) listOf(FRONTEND_MAC_ICON_JVM_ARGUMENT) else emptyList(),
      extraJvmArguments = extraJvmArguments,
      mainClass = frontend.mainClass,
      envVarBaseName = FRONTEND_ENV_VAR_BASE_NAME,
      dataDirectoryName = frontend.systemSelector,
    )
  }

  val customCommands = if (!properties.launcherCustomCommands) emptyList() else buildList {
    if (embeddedFrontend != null) {
      add(frontendCommand(EMBEDDED_FRONTEND_COMMANDS, embeddedFrontend, extraJvmArguments = emptyList()))
    }
    val ijLightFrontend = embeddedFrontend ?: product.takeIf { properties.platformPrefix == "JetBrainsClient" }
    if (ijLightFrontend != null) {
      add(frontendCommand(listOf("ijLight"), ijLightFrontend, extraJvmArguments = IJ_LIGHT_JVM_ARGUMENTS))
    }
    properties.qodanaProductProperties?.let { qodana ->
      add(ProductLaunchCustomCommand(
        commands = listOf("qodana"),
        bootClassPathJarNames = product.bootClassPathJarNames + PLATFORM_CORE_NIO_FS,
        jvmArguments = jvmArguments,
        qodana = true,
        extraJvmArguments = qodana.getAdditionalVmOptions(product.buildNumber),
      ))
    }
    add(ProductLaunchCustomCommand(
      commands = listOf(STDIO_MCP_RUNNER_COMMAND),
      vmOptionsFilePath = OsFamily.ALL.associate { it.osName to stdioMcpRunnerVmOptionsFilePath(it) },
      bootClassPathJarNames = product.bootClassPathJarNames + STDIO_MCP_RUNNER_BOOT_CLASS_PATH_JAR_NAMES,
      mainClass = STDIO_MCP_RUNNER_MAIN_CLASS,
    ))
  }

  return ProductLaunchModel(
    productCode = applicationInfo.productCode,
    envVarBaseName = properties.getEnvironmentVariableBaseName(applicationInfo),
    dataDirectoryName = product.systemSelector,
    minRequiredJavaVersion = minRequiredJavaVersion,
    customProperties = properties.generateCustomPropertiesForProductInfo().map { ProductLaunchProperty(it.key, it.value) },
    flavors = properties.getProductFlavors(),
    jbr17 = bundledRuntimeBuild.startsWith("17."),
    baseFileName = properties.baseFileName,
    languageServer = product.isLanguageServer,
    launch = ProductLaunchCommand(
      mainClass = product.mainClass,
      bootClassPathJarNames = product.bootClassPathJarNames,
      jvmArguments = jvmArguments,
      stdioRedirectArg = properties.stdioRedirectArg,
    ),
    customCommands = customCommands,
    vmOptions = OsFamily.ALL.associate { os ->
      os.osName to generateVmOptions(
        isEAP = applicationInfo.isEAP,
        customMemoryVmOptions = properties.customJvmMemoryOptions,
        additionalVmOptions = buildList {
          addAll(properties.additionalVmOptions)
          addAll(pluginRepositoryVmOptions)
          addAll(osVmOptions(os, properties.platformPrefix))
        },
        platformPrefix = properties.platformPrefix,
        isHeadless = product.isLanguageServer,
      )
    },
    ideaProperties = ProductLaunchIdeaProperties(
      languageServerBase = product.isLanguageServer,
      additions = properties.additionalIDEPropertiesFilePaths.map { Files.readString(it) },
      settingsDir = ideaPropertiesSettingsDir(product.systemSelector),
      suffix = if (product.isLanguageServer) "" else ideaPropertiesFatalErrorNotification(applicationInfo.isEAP),
    ),
  )
}

/** The JVM argument facts of [product]. The build context renders its launch arguments from them too. */
internal fun productJvmArguments(product: ProductLaunchInputs, bundledRuntimeVersion: Int): ProductJvmArguments {
  val properties = product.properties
  val bundledPluginModules = product.bundledPluginModules
  return ProductJvmArguments(
    xBootClassPathJarNames = properties.xBootClassPathJarNames,
    multiRoutingFileSystem = isMultiRoutingFileSystemEnabledForProduct(properties.platformPrefix),
    cdsArchiveFileName = if (properties.enableCds) "${properties.baseFileName}${product.buildNumber}.jsa" else null,
    classLoader = if (properties.enableCds) null else properties.classLoader,
    vendorName = product.applicationInfo.shortCompanyName,
    pathsSelector = product.systemSelector,
    jnaNativeDir = JNA_NATIVE_DIR.takeIf { bundledPluginModules.contains(JNA_PLUGIN_MODULE) },
    pty4jNativeDir = PTY4J_NATIVE_DIR.takeIf { bundledPluginModules.contains(PTY4J_PLUGIN_MODULE) },
    skikoNativeDir = SKIKO_NATIVE_DIR.takeIf { bundledPluginModules.contains(COMPOSE_PLUGIN_MODULE) },
    runtimeModuleRepository = product.useModularLoader || product.generateRuntimeModuleRepository,
    rootModule = if (product.useModularLoader) properties.rootModuleForModularLoader else null,
    productMode = if (product.useModularLoader) properties.productMode.id else null,
    platformPrefix = properties.platformPrefix,
    additional = properties.additionalIdeJvmArguments + properties.getAdditionalContextDependentIdeJvmArguments(product.applicationInfoOf),
    splash = properties.useSplash,
    nativeAccess = bundledRuntimeVersion >= 25,
  )
}

/**
 * The launch model of [properties] as the split dev distribution lays it out, with no build context.
 *
 * It reads what the `platform_resources` fragment of that distribution reads: the development build options, a
 * build number from `build.txt`, and an embedded frontend whose context generates the runtime module repository.
 */
@ApiStatus.Internal
fun computeDevProductLaunchModel(properties: ProductProperties, outputProvider: ModuleOutputProvider, buildDateInSeconds: Long): ProductLaunchModel {
  val project = outputProvider.findRequiredModule(properties.applicationInfoModule).project
  val buildNumber = SnapshotBuildNumber.VALUE
  val applicationInfoOf = { productProperties: ProductProperties -> loadDevDistributionApplicationInfo(project, productProperties, buildDateInSeconds) }

  fun inputs(productProperties: ProductProperties, generateRuntimeModuleRepository: Boolean): ProductLaunchInputs {
    return ProductLaunchInputs(
      properties = productProperties,
      applicationInfo = applicationInfoOf(productProperties),
      buildNumber = buildNumber,
      bundledPluginModules = getBundledPluginModules(productProperties, outputProvider),
      useModularLoader = productProperties.rootModuleForModularLoader != null,
      generateRuntimeModuleRepository = generateRuntimeModuleRepository,
      bootClassPathJarNames = listOf(PLATFORM_LOADER_JAR),
      applicationInfoOf = applicationInfoOf,
    )
  }

  // The fragment generates no runtime module repository. The embedded frontend context copies the build options, and
  // the copy takes the default, which generates one.
  val languageLevel = checkNotNull(JpsJavaExtensionService.getInstance().getProjectExtension(project)?.languageLevel) {
    "Cannot find the project language level"
  }
  return computeProductLaunchModel(
    product = inputs(properties, generateRuntimeModuleRepository = false),
    embeddedFrontend = properties.embeddedFrontendProperties?.invoke()?.let { inputs(it, generateRuntimeModuleRepository = true) },
    minRequiredJavaVersion = languageLevel.feature(),
    bundledRuntimeBuild = DependenciesProperties(COMMUNITY_ROOT).property("runtimeBuild"),
  )
}

private val launchModelJson = Json {
  prettyPrint = true
  prettyPrintIndent = "  "
  encodeDefaults = false
}

/** The JSON the plan generator writes and the `product-files` renderer reads. */
@ApiStatus.Internal
fun encodeProductLaunchModel(model: ProductLaunchModel): String = launchModelJson.encodeToString(ProductLaunchModel.serializer(), model) + "\n"

/** The vmoptions file of a launch, as `product-info.json` names it relative to the file. */
internal fun vmOptionsFilePath(os: OsFamily, baseFileName: String, languageServer: Boolean, hostLanguageServer: Boolean = languageServer): String {
  return when (os) {
    OsFamily.MACOS -> "${if (hostLanguageServer) "" else "../"}bin/$baseFileName.vmoptions"
    OsFamily.LINUX -> "bin/${add64IfNeeded(baseFileName, languageServer)}.vmoptions"
    OsFamily.WINDOWS -> "bin/${add64IfNeeded(baseFileName, languageServer)}.exe.vmoptions"
  }
}

/** The name of the vmoptions file in `bin` of the distribution of [os]. */
@ApiStatus.Internal
fun ProductLaunchModel.vmOptionsFileName(os: OsFamily): String = vmOptionsFileName(os, baseFileName, languageServer)

/** The vmoptions file of a launch, as the distribution holds it. */
internal fun vmOptionsFileName(os: OsFamily, baseFileName: String, languageServer: Boolean): String {
  return when (os) {
    OsFamily.MACOS -> "$baseFileName.vmoptions"
    OsFamily.LINUX -> "${add64IfNeeded(baseFileName, languageServer)}.vmoptions"
    OsFamily.WINDOWS -> "${add64IfNeeded(baseFileName, languageServer)}.exe.vmoptions"
  }
}

/** `BuildContext.add64IfNeeded`. */
internal fun add64IfNeeded(name: String, languageServer: Boolean): String = if (languageServer) name else "${name}64"
