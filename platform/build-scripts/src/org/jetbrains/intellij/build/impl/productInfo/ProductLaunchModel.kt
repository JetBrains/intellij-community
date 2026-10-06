// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo

import com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct
import kotlinx.serialization.SerialName
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
import org.jetbrains.intellij.build.impl.getBundledPluginModules
import org.jetbrains.intellij.build.impl.hasIcnsForFrontendMacApp
import org.jetbrains.intellij.build.impl.ideaPropertiesSettingsDir
import org.jetbrains.intellij.build.impl.memoryVmOptions
import org.jetbrains.intellij.build.impl.multiRoutingFileSystemVmOptions
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
 * The model states each product fact once. It states no value that the version, the suffix, the release date, the EAP flag
 * or the build number decide. It keeps the product code, the environment variable name and the data directory base name.
 * The tool appends the version to the base name, and it reads the vendor from the application info. The tool reads the
 * application info sources and `build.txt` for the names, the version, the suffix, the icon, the release date, the EAP
 * flag, the build number and the Linux window class. It reads the common vmoptions lines from `bin/common.vmoptions`.
 */
@ApiStatus.Internal
@Serializable
data class ProductLaunchModel(
  @JvmField val productCode: String,
  @JvmField val envVarBaseName: String,
  /**
   * The data directory name without the version: what [ideaPropertiesSettingsDir] returns for the system selector. The
   * tool appends `<majorVersion>.<minorVersionMainPart>` of the application info.
   */
  @JvmField val dataDirectoryBaseName: String,
  @JvmField val minRequiredJavaVersion: Int,
  @JvmField val customProperties: List<ProductLaunchProperty> = emptyList(),
  /** The flavors of the product. A launch that bundles a runtime lists `jbr17` before them when [jbr17] is set. */
  @JvmField val flavors: List<String> = emptyList(),
  @JvmField val jbr17: Boolean = false,
  @JvmField val baseFileName: String,
  /** A language server keeps `bin` at the root of the macOS distribution and adds no `64` to its file names. */
  @JvmField val languageServer: Boolean = false,
  @JvmField val launch: ProductLaunchCommand,
  /** The JVM arguments of the frontend that a custom command names by [JvmArgumentsRef.FRONTEND]. */
  @JvmField val frontendJvmArguments: ProductJvmArguments? = null,
  @JvmField val customCommands: List<ProductLaunchCustomCommand> = emptyList(),
  @JvmField val vmOptions: ProductVmOptions,
  @JvmField val ideaProperties: ProductLaunchIdeaProperties,
)

/**
 * The parts of the vmoptions file of a release build: [memory], the lines of `bin/common.vmoptions`, [product], then the
 * lines of the OS in [os].
 *
 * The tool inserts the line of an EAP build, see [org.jetbrains.intellij.build.impl.insertEapVmOptions]. For a language
 * server, it then applies the headless rule of [org.jetbrains.intellij.build.impl.generateVmOptions].
 */
@ApiStatus.Internal
@Serializable
data class ProductVmOptions(
  /** The memory lines, see [memoryVmOptions]. */
  @JvmField val memory: List<String>,
  /** The multi-routing file system lines, then the additional lines of the product. */
  @JvmField val product: List<String> = emptyList(),
  /** The lines of each OS, keyed by [OsFamily.osName]. An OS without an entry adds no line. */
  @JvmField val os: Map<String, List<String>> = emptyMap(),
)

@ApiStatus.Internal
@Serializable
data class ProductLaunchProperty(@JvmField val key: String, @JvmField val value: String)

/** The main launch of a product. */
@ApiStatus.Internal
@Serializable
data class ProductLaunchCommand(
  @JvmField val mainClass: String,
  @JvmField val bootClassPathJarNames: List<String> = listOf(PLATFORM_LOADER_JAR),
  @JvmField val jvmArguments: ProductJvmArguments,
  @JvmField val stdioRedirectArg: String? = null,
)

/**
 * One custom command of the launch, such as `thinClient`.
 *
 * Its JVM arguments are the block that [jvmArguments] names, rendered for the OS and architecture, then
 * [macJvmArguments] on macOS, then [extraJvmArguments]. The tool replaces [BUILD_NUMBER_TOKEN] in [extraJvmArguments]
 * with the build number. A command that states [envVarBaseName] also states the data directory name of the product.
 */
@ApiStatus.Internal
@Serializable
data class ProductLaunchCustomCommand(
  @JvmField val commands: List<String>,
  /** The vmoptions file of the command, keyed by [OsFamily.osName]. A command with no entry names none. */
  @JvmField val vmOptionsFilePath: Map<String, String> = emptyMap(),
  @JvmField val bootClassPathJarNames: List<String> = emptyList(),
  @JvmField val jvmArguments: JvmArgumentsRef? = null,
  /** Renders [jvmArguments] the way Qodana starts: without the multi-routing file system. */
  @JvmField val qodana: Boolean = false,
  @JvmField val macJvmArguments: List<String> = emptyList(),
  @JvmField val extraJvmArguments: List<String> = emptyList(),
  @JvmField val mainClass: String? = null,
  @JvmField val envVarBaseName: String? = null,
)

/** The block of JVM arguments that a custom command renders. */
@ApiStatus.Internal
@Serializable
enum class JvmArgumentsRef {
  /** [ProductLaunchCommand.jvmArguments] of [ProductLaunchModel.launch]. */
  @SerialName("launch")
  LAUNCH,

  /** [ProductLaunchModel.frontendJvmArguments]. */
  @SerialName("frontend")
  FRONTEND,
}

/**
 * The facts `BuildContext.getAdditionalJvmArguments` reads. [renderAdditionalJvmArguments] adds the vendor, the system
 * selector, the OS and the architecture, and the `--add-opens` lines of the OS.
 */
@ApiStatus.Internal
@Serializable
data class ProductJvmArguments(
  @JvmField val xBootClassPathJarNames: List<String> = emptyList(),
  /** Whether the multi-routing file system is on for the product, which puts [PLATFORM_CORE_NIO_FS] on the boot class path. */
  @JvmField val multiRoutingFileSystem: Boolean = true,
  /** The file name of the CDS archive, when the product enables CDS. */
  @JvmField val cdsArchiveFileName: String? = null,
  @JvmField val classLoader: String? = DEFAULT_CLASS_LOADER,
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
  @JvmField val nativeAccess: Boolean = true,
)

/** The default of [ProductProperties.classLoader]. */
private const val DEFAULT_CLASS_LOADER = "com.intellij.util.lang.PathClassLoader"

/**
 * The parts of `bin/idea.properties`: the base file, then each of [additions] after a newline, with
 * `@@settings_dir@@` replaced by [ProductLaunchModel.dataDirectoryBaseName].
 */
@ApiStatus.Internal
@Serializable
data class ProductLaunchIdeaProperties(
  /** The base file is `language-server/build/idea.properties`, not `community/bin/idea.properties`. */
  @JvmField val languageServerBase: Boolean = false,
  @JvmField val additions: List<String> = emptyList(),
  /** When set, the tool appends `ideaPropertiesFatalErrorNotification(isEAP)` with the EAP flag of the application info. */
  @JvmField val fatalErrorNotification: Boolean = false,
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

/** The text in a custom command argument that the tool replaces with the build number of `build.txt`. */
private const val BUILD_NUMBER_TOKEN = "@@build_number@@"

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
  val dataDirectoryBaseName = ideaPropertiesSettingsDir(product.systemSelector)
  check(product.systemSelector == "$dataDirectoryBaseName${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}") {
    "The system selector ${product.systemSelector} of ${properties.platformPrefix} must be the data directory base name " +
    "$dataDirectoryBaseName, then the major version and the main part of the minor version, because the tool renders it so"
  }
  val frontendMacIcon = hasIcnsForFrontendMacApp(properties.imagesDirectoryPath, isEap = false)
  check(frontendMacIcon == hasIcnsForFrontendMacApp(properties.imagesDirectoryPath, isEap = true)) {
    "${properties.imagesDirectoryPath} must hold both product_frontend.icns and product_frontend_EAP.icns or neither, " +
    "because the launch model states no EAP flag"
  }

  fun frontendCommand(commands: List<String>, frontend: ProductLaunchInputs, extraJvmArguments: List<String>): ProductLaunchCustomCommand {
    check(frontend.systemSelector == product.systemSelector) {
      "The frontend ${frontend.properties.platformPrefix} has the system selector ${frontend.systemSelector}, but the product " +
      "has ${product.systemSelector}. The tool renders the data directory name of the product for a frontend command."
    }
    check(frontend.applicationInfo.shortCompanyName == applicationInfo.shortCompanyName) {
      "The frontend ${frontend.properties.platformPrefix} has the vendor ${frontend.applicationInfo.shortCompanyName}, but the " +
      "product has ${applicationInfo.shortCompanyName}. The tool renders the vendor of the product for a frontend command."
    }
    return ProductLaunchCustomCommand(
      commands = commands,
      vmOptionsFilePath = OsFamily.ALL.associate { os ->
        os.osName to vmOptionsFilePath(os, frontend.properties.baseFileName, frontend.isLanguageServer, hostLanguageServer = product.isLanguageServer)
      },
      bootClassPathJarNames = frontend.bootClassPathJarNames,
      jvmArguments = JvmArgumentsRef.FRONTEND,
      macJvmArguments = if (frontendMacIcon) listOf(FRONTEND_MAC_ICON_JVM_ARGUMENT) else emptyList(),
      extraJvmArguments = extraJvmArguments,
      mainClass = frontend.mainClass,
      envVarBaseName = FRONTEND_ENV_VAR_BASE_NAME,
    )
  }

  // The frontend of the `ijLight` command: the embedded frontend, else a client product itself.
  val ijLightFrontend = if (properties.launcherCustomCommands) {
    embeddedFrontend ?: product.takeIf { properties.platformPrefix == "JetBrainsClient" }
  }
  else {
    null
  }
  val customCommands = if (!properties.launcherCustomCommands) emptyList() else buildList {
    if (embeddedFrontend != null) {
      add(frontendCommand(EMBEDDED_FRONTEND_COMMANDS, embeddedFrontend, extraJvmArguments = emptyList()))
    }
    if (ijLightFrontend != null) {
      add(frontendCommand(listOf("ijLight"), ijLightFrontend, extraJvmArguments = IJ_LIGHT_JVM_ARGUMENTS))
    }
    properties.qodanaProductProperties?.let { qodana ->
      add(ProductLaunchCustomCommand(
        commands = listOf("qodana"),
        bootClassPathJarNames = product.bootClassPathJarNames + PLATFORM_CORE_NIO_FS,
        jvmArguments = JvmArgumentsRef.LAUNCH,
        qodana = true,
        extraJvmArguments = qodana.getAdditionalVmOptions(BUILD_NUMBER_TOKEN),
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
    dataDirectoryBaseName = dataDirectoryBaseName,
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
    frontendJvmArguments = ijLightFrontend?.let { productJvmArguments(it, bundledRuntimeVersion) },
    customCommands = customCommands,
    vmOptions = ProductVmOptions(
      memory = memoryVmOptions(properties.customJvmMemoryOptions),
      product = buildList {
        addAll(multiRoutingFileSystemVmOptions(properties.platformPrefix))
        addAll(properties.additionalVmOptions)
        addAll(pluginRepositoryVmOptions)
      },
      os = OsFamily.ALL.associate { it.osName to osVmOptions(it, properties.platformPrefix) }.filterValues { it.isNotEmpty() },
    ),
    ideaProperties = ProductLaunchIdeaProperties(
      languageServerBase = product.isLanguageServer,
      additions = properties.additionalIDEPropertiesFilePaths.map { Files.readString(it) },
      fatalErrorNotification = !product.isLanguageServer,
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
