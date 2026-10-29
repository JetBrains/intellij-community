// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.util.annotations.VisibleToClasses
import com.intellij.platform.util.annotations.paths.NioPath
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import com.intellij.platform.eel.provider.utils.WindowsPathUtils
import com.intellij.platform.eel.provider.utils.impl.localToIjent
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.nio.file.Paths
import java.util.logging.Logger

private val LOG = Logger.getLogger("com.intellij.platform.eel.provider.EelNioBridge")

/**
 * Converts [EelPath], which is likely a path from a remote machine, to a [Path] for the local machine.
 *
 * Example:
 * ```kotlin
 * EelPath.parse("/home/user", getWslDescriptorSomewhere()).asNioPath() ==
 *     Path.of("""\\wsl.localhost\Ubuntu\home\user""")
 *
 * EelPath.parse("/home/user", getDocekrDescriptorSomewhere()).asNioPath() ==
 *     Path.of("""\\docker\f00b4r\home\user""")
 * ```
 *
 * @throws IllegalArgumentException if the Eel API for [this] does not have a corresponding [java.nio.file.FileSystem]
 */
@Throws(IllegalArgumentException::class)
@ApiStatus.Experimental
@ApiStatus.Internal
@VisibleToClasses(
    "com.goide.execution.target.GoCommandLineSetup",
    "com.intellij.aiagentextensions.contrib.shared.commandmonitor.CodexCommandMonitor",
    "com.intellij.aiagentextensions.contrib.shared.commandmonitor.CodexCommandMonitorKt",
    "com.intellij.aiagentextensions.skills.settings.ProjectAgentHome",
    "com.intellij.air.acp.AcpHookBundleDeploymentKt",
    "com.intellij.air.acp.aiagentextensions.AcpSkillsAdditionalRootsProviderKt",
    "com.intellij.air.acp.aiagentextensions.AgentSkillPresentationProviderImpl",
    "com.intellij.air.acp.pool.AcpAgentClient",
    "com.intellij.air.acp.pool.AcpProcessDirectory",
    "com.intellij.air.acp.runtime.process.AcpCommandResolverKt",
    "com.intellij.air.acp.runtime.process.EelAwarePathConverter",
    "com.intellij.air.backend.claude.threads.ClaudeHookBridgeKt",
    "com.intellij.air.backend.claude.threads.ClaudeStoreAgentMachineTest",
    "com.intellij.air.backend.claude.threads.RemoteClaudeDaemonLauncherFactory",
    "com.intellij.air.backend.claude.threads.RemoteClaudeStoreFactory",
    "com.intellij.air.backend.codex.common.CodexCliUtils",
    "com.intellij.air.backend.codex.common.CodexExecutableIdentityServiceImpl",
    "com.intellij.air.backend.codex.common.CodexWebSocketAppServerClient",
    "com.intellij.air.backend.codex.threads.CodexMachineManagersTest",
    "com.intellij.air.backend.codex.threads.CodexWorktreesKt",
    "com.intellij.air.backend.codex.threads.backend.appserver.CodexAppServerManagerKt",
    "com.intellij.air.backend.codex.threads.backend.appserver.SharedCodexAppServerService",
    "com.intellij.air.backend.copilot.threads.CopilotAgentKt",
    "com.intellij.air.backend.copilot.threads.CopilotHooksMachineTest",
    "com.intellij.air.backend.copilot.threads.CopilotMachineStoreTest",
    "com.intellij.air.backend.copilot.threads.CopilotSessionIndexKt",
    "com.intellij.air.backend.filewatch.WatchedPathRoutingKt",
    "com.intellij.air.backend.junie.threads.JunieMachineStoresKt",
    "com.intellij.air.backend.junie.threads.JunieMachineStoresTest",
    "com.intellij.air.backend.junie.threads.JunieSessionSourceKt",
    "com.intellij.air.backend.opencode.threads.OpenCodeMachineServersTest",
    "com.intellij.air.backend.opencode.threads.server.SharedOpenCodeServerService",
    "com.intellij.air.backend.pi.threads.PiExtensionChannel",
    "com.intellij.air.backend.pi.threads.PiMachineSessionsTest",
    "com.intellij.air.backend.pi.threads.PiModelCatalogProbeServiceImplKt",
    "com.intellij.air.backend.pi.threads.PiSessionSourceKt",
    "com.intellij.air.backend.pi.threads.PiThemeSupport",
    "com.intellij.air.backend.session.runtime.AgentThreadProjectCatalogMachineLeaseTest",
    "com.intellij.air.backend.session.runtime.AgentUserActionMachineLeaseTest",
    "com.intellij.air.backend.session.runtime.git.GitWorktreeDiscovery",
    "com.intellij.air.backend.session.runtime.git.GitWorktreeDiscoveryKt",
    "com.intellij.air.backend.session.runtime.service.AgentSessionVfsObservationCoordinatorKt",
    "com.intellij.air.backend.session.view.gui.references.ThreadGuiFileReferenceResolver",
    "com.intellij.air.backend.workflows.conditions.bash.WorkflowBashExecutable",
    "com.intellij.air.backend.worktree.ManagedWorktreeServiceKt",
    "com.intellij.air.backend.worktree.ManagedWorktreeSnapshotServiceTest",
    "com.intellij.air.backend.worktree.ManagedWorktreeStoreTest",
    "com.intellij.air.codeNotes.api.CodeNoteFilePaths",
    "com.intellij.air.frontend.core.AirEelFileChooserContributor",
    "com.intellij.air.frontend.core.AirEelFileChooserContributorTest",
    "com.intellij.air.frontend.session.view.gui.mcp.render.renderers.resourcelink.LocalImageResourceLoader",
    "com.intellij.air.frontend.workflows.files.WorkflowFileWriter",
    "com.intellij.air.frontend.worktree.settings.AirWorktreeParentLocationServiceImpl",
    "com.intellij.air.frontend.worktree.settings.WorktreeSettingsUtils",
    "com.intellij.air.light.extensions.host.LightExtensionBoundaryTest",
    "com.intellij.air.light.extensions.host.LightExtensionRuntimeInstaller",
    "com.intellij.air.light.extensions.host.LightExtensionRuntimeLayout",
    "com.intellij.air.light.extensions.host.LightExtensionRuntimeRoutes",
    "com.intellij.air.shared.core.AgentFilePathsKt",
    "com.intellij.air.shared.core.AirPathUtilKt",
    "com.intellij.air.shared.core.LinkedGitWorktrees",
    "com.intellij.air.shared.core.LinkedGitWorktreesMachineTest",
    "com.intellij.air.shared.core.machine.AgentMachineKt",
    "com.intellij.clion.stm32.core.CubeWrapperDetectionKt",
    "com.intellij.clion.stm32.core.UtilsKt",
    "com.intellij.code.findings.cli.frontend.FindingsTerminalLaunchCollectorKt",
    "com.intellij.compiler.server.BuildManagerSdkSelectionTest",
    "com.intellij.database.connectivity.artifacts.MavenSettingsRepositoriesProvider",
    "com.intellij.database.run.EelDatabaseHelperKt",
    "com.intellij.docker.agent.eel.EelFacade",
    "com.intellij.docker.cli.CliExecutor",
    "com.intellij.eel.ultimate.testFramework.EelHeavyIdeaTestFixturePathProvider",
    "com.intellij.embark.cli.EmbarkCliLayout",
    "com.intellij.execution.eel.EelSystemFolderTest",
    "com.intellij.execution.portsWatcher.impl.LinuxPortsScanningKt",
    "com.intellij.execution.target.EelTargetEnvironment",
    "com.intellij.execution.target.local.LocalTargets",
    "com.intellij.externalProcessAuthHelper.ExternalProcessHandlerService",
    "com.intellij.find.impl.EelSearchEdges",
    "com.intellij.go.fix.inspection.GoFixGlobalInspectionContext",
    "com.intellij.go.linter.platform.PlatformKt",
    "com.intellij.go.performance.escapeAnalysis.GoEscapeAnalysisPathsKt",
    "com.intellij.ide.actions.OpenFileAction",
    "com.intellij.ide.starter.extended.idea.ultimate.IdeaUltimateCases",
    "com.intellij.ide.starter.extended.path.StarterExtendedGlobalPathsKt",
    "com.intellij.ide.starter.extended.targets.Target",
    "com.intellij.ide.starter.extended.targets.docker.PrebuiltDockerImages",
    "com.intellij.ide.starter.utils.FileSystem",
    "com.intellij.javascript.nodejs.interpreter.eel.EelNodeInterpreterUtilKt",
    "com.intellij.ml.llm.agents.acp.runtime.registry.AcpPaths",
    "com.intellij.openapi.application.JDKNameTest",
    "com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtilKt",
    "com.intellij.openapi.fileChooser.universal.UniversalFileChooser",
    "com.intellij.openapi.projectRoots.impl.EelSystemInfoProvider",
    "com.intellij.openapi.projectRoots.impl.JavaHomeFinderEel",
    "com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller",
    "com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstallerEel",
    "com.intellij.openapi.util.io.FileAttributesReadingTest",
    "com.intellij.openapi.vfs.impl.eel.EelFileWatcher",
    "com.intellij.openapi.vfs.impl.local.LocalFileSystemEelUtil",
    "com.intellij.php.terminal.backend.PhpTerminalCustomizer",
    "com.intellij.platform.daemon.connection.api.RemoteProjectChooserPanel",
    "com.intellij.platform.daemon.connection.api.SingleHostFileChooserContributor",
    "com.intellij.platform.eel.impl.local.LocalEelApiImplKt",
    "com.intellij.platform.eel.impl.local.LocalEelArchiveApiImpl",
    "com.intellij.platform.eel.impl.local.LocalEelFileSystemApiKt",
    "com.intellij.platform.eel.impl.local.NioBasedEelFileSystemApi",
    "com.intellij.platform.eel.nioFs.impl.MountsAwareFileSystemProvider",
    "com.intellij.platform.eel.provider.RemoteProjectPathProviderKt",
    "com.intellij.platform.eel.provider.nioHelpers.EelFileUtilsTest",
    "com.intellij.platform.eel.provider.nioHelpers.EelFilesTest",
    "com.intellij.platform.eel.provider.utils.EelExternalAppEntry",
    "com.intellij.platform.eel.provider.utils.EelProjectUtils",
    "com.intellij.platform.eel.provider.utils.EelSystemFolderUtils",
    "com.intellij.platform.frontend.split.baseline.eel.RdEelCanonicalizationKt",
    "com.intellij.platform.frontend.split.baseline.fs.LightFileIdAdapter",
    "com.intellij.platform.frontend.split.baseline.light.IjLightLaunchArgsKt",
    "com.intellij.platform.ijent.functional.EelIoTest",
    "com.intellij.platform.ijent.functional.EelParametrizedTest",
    "com.intellij.platform.ijent.functional.EelTargetEnvironmentTest",
    "com.intellij.platform.ijent.functional.EelTargetEnvironmentWslTest",
    "com.intellij.platform.ijent.functional.GeneralCommandLineEelStrictTest",
    "com.intellij.platform.ijent.functional.IjentSpecialCharsTest",
    "com.intellij.platform.ijent.functional.IjentWslTest",
    "com.intellij.platform.ijent.functional.LocalhostTcpEelMachineTest",
    "com.intellij.platform.ijent.functional.RdEelHostMachineTest",
    "com.intellij.platform.ijent.functional.TcpLocalBootstrapTest",
    "com.intellij.platform.ijent.functional.fs.AutoCloseFileOptimizationTest",
    "com.intellij.platform.ijent.functional.fs.EelFileSystemTest2",
    "com.intellij.platform.ijent.functional.fs.IjentFileSystemTest",
    "com.intellij.platform.ijent.functional.fs.IncrementalFileTransferTest",
    "com.intellij.platform.ijent.functional.fs.MultiRoutingFileSystemLocaleTest",
    "com.intellij.platform.ijent.functional.fs.SearchTest",
    "com.intellij.platform.ijent.functional.fs.StreamingReadTest",
    "com.intellij.platform.ijent.functional.fs.StreamingWriteTest",
    "com.intellij.platform.ijent.functional.fs.WalkDirectoryTest",
    "com.intellij.platform.ijent.functional.fs.filewatcher.EelUnixFileWatcherTest",
    "com.intellij.platform.ijent.functional.fs.filewatcher.EelWindowsFileWatcherTest",
    "com.intellij.platform.ijent.functional.processes.EelExecApiTest",
    "com.intellij.platform.ijent.functional.processes.EelProcessManagementApiTest",
    "com.intellij.platform.ijent.performance.benchmarks.EelIncrementalWalkingTransferBenchmarkTest",
    "com.intellij.platform.ijent.performance.benchmarks.IJentZipFileReadBenchmarkTest",
    "com.intellij.platform.ijent.ssh.SshMrfsTest",
    "com.intellij.platform.ijent.testFramework.EelServicesConsistencyTest",
    "com.intellij.platform.ijent.testFramework.common.EelTestEnvironmentHolder",
    "com.intellij.platform.ijent.testFramework.common.IdeaEelProjectTestFixture",
    "com.intellij.platform.lsp.api.LspClientDescriptor",
    "com.intellij.platform.testFramework.eelJava.EelTestJdkProvider",
    "com.intellij.platform.testFramework.junit5.eel.impl.UtilKt",
    "com.intellij.platform.testFramework.junit5.eel.showcase.EelFsShowcase",
    "com.intellij.python.community.execService.ApiKt",
    "com.intellij.python.community.execService.impl.processLaunchers.EelKt",
    "com.intellij.python.community.execService.impl.processLaunchers.EelProcessCommands",
    "com.intellij.python.community.services.systemPython.impl.providers.AsdfSystemPythonProvider",
    "com.intellij.python.community.services.systemPython.impl.providers.PyenvSystemPythonProvider",
    "com.intellij.python.dap.PyDapLogHandler",
    "com.intellij.python.dap.PyRunInTerminalSupportKt",
    "com.intellij.python.dap.environment.DebugpyExecEnvironmentImplKt",
    "com.intellij.python.hatch.service.CliBasedHatchProjectStructureService",
    "com.intellij.python.junit5Tests.debugger.PyEelSdkDebuggerTest",
    "com.intellij.python.junit5Tests.env.HelpersShowCaseTest",
    "com.intellij.python.junit5Tests.env.systemPython.impl.BinaryStampEelTest",
    "com.intellij.python.junit5Tests.env.terminal.PyVirtualEnvTerminalCustomizerTest",
    "com.intellij.python.junit5Tests.env.tests.PythonSdkUtilGetAllSdksOnEelTest",
    "com.intellij.python.junit5Tests.env.tests.VirtualEnvReaderEelTest",
    "com.intellij.python.junit5Tests.unit.ExecLimiterTest",
    "com.intellij.python.junit5Tests.unit.PyDebugpyEelEnvironmentTest",
    "com.intellij.python.junit5Tests.unit.alsoWin.ExecServicePortForwardingTest",
    "com.intellij.python.junit5Tests.unit.alsoWin.ExecServiceShowCaseTest",
    "com.intellij.python.junit5Tests.unit.alsoWin.ExecuteServiceTest",
    "com.intellij.python.junit5Tests.unit.alsoWin.MapPathTest",
    "com.intellij.python.junit5Tests.unit.alsoWin.PathShortenerTest",
    "com.intellij.python.junit5Tests.unit.alsoWin.ProcessOutputTransformerShowCaseTest",
    "com.intellij.python.junit5Tests.unit.alsoWin.PyExecServiceHandlerTest",
    "com.intellij.python.junit5Tests.unit.alsoWin.PyHelperUploadTest",
    "com.intellij.python.junit5Tests.unit.alsoWin.RunExecutableTest",
    "com.intellij.python.pixi.backend.PixiPyToolKt",
    "com.intellij.python.pytools.backend.PyToolApiImpl",
    "com.intellij.python.pytools.backend.impl.EelExecutableDetectorKt",
    "com.intellij.python.ruff.codeinsight.RuffConfigCheckKt",
    "com.intellij.python.terminal.PyVirtualEnvTerminalCustomizer",
    "com.intellij.python.terminal.shared.PathDto",
    "com.intellij.rustrover.solana.runconfig.anchor.AnchorTestCommandLineStateTest",
    "com.intellij.rustrover.solana.settings.SolanaConfigurableKt",
    "com.intellij.rustrover.solana.util.SolanaConfigKt",
    "com.intellij.rustrover.solana.util.SolanaEelEnvironment",
    "com.intellij.sh.backend.ShDocumentationProvider",
    "com.intellij.sh.markdown.ShMarkdownRunner",
    "com.intellij.sh.run.ShShellDetection",
    "com.intellij.sh.terminal.frontend.ShFrontendTerminalRunner",
    "com.intellij.ssh.impl.eel.channels.EelSftpChannel",
    "com.intellij.terminal.backend.hyperlinks.filter.TerminalFileLookupKt",
    "com.intellij.terminal.backend.rpc.TerminalExecOptionsCustomizationRemoteApiImplKt",
    "com.intellij.terminal.frontend.action.RevealFileInReworkedTerminalAction",
    "com.intellij.terminal.frontend.dnd.TerminalDroppedContentResolver",
    "com.intellij.terminal.frontend.settings.TerminalOptionsConfigurable",
    "com.intellij.terminal.frontend.settings.WelcomeProjectTerminalWorkingDirectoryCustomizer",
    "com.intellij.terminal.frontend.toolwindow.impl.TerminalClipboard",
    "com.intellij.terminal.tests.eel.ShellExecOptionsCustomizerTest",
    "com.intellij.terminal.tests.eel.TerminalCustomizerLocalPathTranslatorTest",
    "com.intellij.terminal.tests.eel.TerminalStartupConfigurationTestKt",
    "com.intellij.testFramework.junit5.fixture.FixturesKt",
    "com.jetbrains.cidr.toolchains.eel.CidrEelHost",
    "com.jetbrains.ls.api.features.impl.go.environment.LSGoDefaultSdkResolver",
    "com.jetbrains.ls.api.features.impl.go.environment.LSGoToolchainService",
    "com.jetbrains.php.config.interpreters.PhpSdkTypeKt",
    "com.jetbrains.php.config.interpreters.eel.PhpEelInterpreterDetectionKt",
    "com.jetbrains.python.debugger.PyEelPathMapper",
    "com.jetbrains.python.pathValidation.PlatformAndRoot",
    "com.jetbrains.python.sdk.add.v2.EelFileSystem",
    "com.jetbrains.python.sdk.targetsFacade.PyTargetsIntrospectionFacadeLocal",
    "com.jetbrains.python.venvReader.VirtualEnvReader",
    "com.jetbrains.rider.azureFunctions.azurite.AzuritePathService",
    "com.jetbrains.rider.azureFunctions.coreTools.AzureFunctionsCoreToolsManager",
    "com.jetbrains.rider.azureFunctions.coreTools.AzureFunctionsToolingFeedService",
    "com.jetbrains.rider.build.ExtendedBuildEvent",
    "com.jetbrains.rider.build.redesign.BuildToolWindowWidgetKt",
    "com.jetbrains.rider.debugger.attach.remoting.DebuggerForRemoteEnvironmentProvider",
    "com.jetbrains.rider.debugger.util.DebuggerPathExtKt",
    "com.jetbrains.rider.environment.remote.RemoteEnvironmentAccessor",
    "com.jetbrains.rider.environment.remote.deploy.files.RemoteEnvironmentFilesManager",
    "com.jetbrains.rider.environment.remote.deploy.processing.DeploymentResourcesUploader",
    "com.jetbrains.rider.ijent.paths.EnvironmentAwareMapper",
    "com.jetbrains.rider.ijent.paths.EnvironmentUnawarePathMapper",
    "com.jetbrains.rider.ijent.vfs.RiderIjentVfsOperations",
    "com.jetbrains.rider.infrastructure.DotNetSdkCmd",
    "com.jetbrains.rider.launchers.inRiderEnv.InRiderEnvironmentBundledAssemblyLauncher",
    "com.jetbrains.rider.test.cases.infrastructure.DotNetSdkCmdTests",
    "com.jetbrains.rider.test.framework.toolsDownloading.RemoteTestToolsDownloader",
    "com.jetbrains.rider.test.framework.workDirectory.ExecutionTargetAwareTestWorkDirectoryStorage",
    "com.jetbrains.rider.unitTesting.util.UnitTestPathExtKt",
    "com.jetbrains.rider.web.DevCertificateService",
    "git4idea.commit.signing.EelAgentPathsLocator",
    "git4idea.config.GitEelExecutableDetectionHelper",
    "git4idea.config.GitExecutable",
    "git4idea.config.GitExecutableManagerEelDockerTest",
    "git4idea.test.GitExecutor",
    "git4idea.workingTrees.GitCreateWorkingTreeService",
    "git4idea.workingTrees.GitCreateWorkingTreeServiceEelTest",
    "org.intellij.terraform.runtime.TfToolPathDetectorImpl",
    "org.jetbrains.idea.maven.execution.run.MavenShCommandLineState",
    "org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes.Maven4ModelVersionErrorParser",
    "org.jetbrains.idea.maven.project.MavenSettingsCache",
    "org.jetbrains.idea.maven.server.eel.EelRemotePathTransformFactory",
    "org.jetbrains.idea.maven.toolchains.ToolchainResolverSession",
    "org.jetbrains.idea.maven.utils.MavenEelUtil",
    "org.jetbrains.idea.maven.utils.MavenUtil",
    "org.jetbrains.idea.maven.utils.MavenUtilTest",
    "org.jetbrains.idea.maven.wizards.MavenModuleBuilderHelper",
    "org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayoutService",
    "org.jetbrains.kotlin.idea.fir.extensions.KtCompilerPluginsCache",
    "org.jetbrains.plugins.gradle.dependency.analyzer.GradleDependencyNodeIndex",
    "org.jetbrains.plugins.gradle.execution.build.GradleProjectTaskRunner",
    "org.jetbrains.plugins.gradle.service.execution.GradleExecutionReporterImpl",
    "org.jetbrains.plugins.gradle.service.execution.GradleUserHomeUtil",
    "org.jetbrains.plugins.gradle.service.execution.eel.EelTargetEnvironmentConfigurationProvider",
    "org.jetbrains.plugins.gradle.util.GradleArtifactDownloader",
    "org.jetbrains.plugins.ruby.ruby.run.eel.RubyEelRunner",
    "org.jetbrains.plugins.ruby.ruby.sdk.eel.RubyEelSdkAdditionalData",
    "org.jetbrains.plugins.ruby.statistic.MiseRubyVersionCollector",
    "org.jetbrains.plugins.ruby.utils.RubyEelUtilsKt",
    "org.jetbrains.plugins.ruby.version.management.SdkRefresherVmBase",
    "org.jetbrains.plugins.ruby.version.management.mise.MiseCli",
    "org.jetbrains.plugins.ruby.version.management.system.RubyWslSystemSdkRefresher",
    "org.jetbrains.plugins.terminal.ShellStartupOptions",
    "org.jetbrains.plugins.terminal.TerminalStartupKt",
    "org.jetbrains.plugins.terminal.hyperlinks.TerminalFileHyperlinkInfo",
    "org.jetbrains.plugins.terminal.settings.impl.TerminalProjectOptionsMigration",
    "org.jetbrains.plugins.terminal.settings.impl.TerminalTabsStorageMigration",
    "org.jetbrains.plugins.terminal.util.TerminalUtilKt",
    "org.jetbrains.plugins.terminal.view.shellIntegration.history.ShellCommandHistoryManager",
    "org.rust.cargo.toolchain.eel.RsEelToolchain",
    "org.rust.cargo.toolchain.flavors.CargoToolchainFlavor",
    "org.rust.cargo.toolchain.flavors.RsMacToolchainFlavor",
    "org.rust.cargo.toolchain.flavors.RsUnixToolchainFlavor",
    "org.rust.cargo.toolchain.flavors.RsWinToolchainFlavor",
    "org.rust.debugger.RsDebuggerToolchainService",
)
fun EelPath.asNioPath(): Path {
  if (descriptor === LocalEelDescriptor) {
    return Paths.get(toString())
  }

  // Comparing strings because `Path.of("\\wsl.localhost\distro\").equals(Path.of("\\wsl$\distro\")) == true`
  // If the project works with `wsl$` paths, this function must return `wsl$` paths, and the same for `wsl.localhost`.
  val root = (descriptor as? EelPathBoundDescriptor)?.rootPath
             ?: throw IllegalArgumentException("Could not convert $this to NIO path, descriptor is $descriptor")

  if (LOG.isLoggable(java.util.logging.Level.FINEST)) {
    LOG.finest("asNioPathOrNull(): path=$this descriptor=$descriptor rootPath=$root")
  }

  @NioPath
  val result = when (descriptor.osFamily) {
    EelOsFamily.Windows -> {
      WindowsPathUtils.resolveEelPathOntoRoot(root, this)
    }
    EelOsFamily.Posix -> parts.fold(root, Path::resolve)
  }
  if (LOG.isLoggable(java.util.logging.Level.FINEST)) {
    LOG.finest("asNioPathOrNull(): path=$this basePath=$root result=$result")
  }
  return result
}

/**
 * Converts a path generated by the default NIO filesystem to [EelPath].
 *
 * Example:
 * ```kotlin
 * Path.of("""C:\Windows""").asEelPath() ==
 *     EelPath.parse("""C:\Windows""", LocalEelDescriptor)
 *
 * Path.of("""\\wsl$\Ubuntu\usr""").asEelPath() ==
 *     EelPath.parse("/usr", someWslDescriptor)
 * ```
 *
 * @throws EelPathException if the passed path is not an absolute path.
 */
@Throws(EelPathException::class)
@ApiStatus.Experimental
fun Path.asEelPath(): EelPath {
  val descriptor = getEelDescriptor()
  if (descriptor is LocalEelDescriptor) {
    return EelPath.parse(toString(), descriptor)
  }

  if (descriptor !is EelPathBoundDescriptor) {
    throw NoSuchElementException("Cannot find a root for $this")
  }

  if (EelNioFsBackend.instance?.isRoutingAware(this) != true) {
    // Path is not managed by a routing-aware filesystem provider — parse as-is.
    return EelPath.parse(toString(), descriptor)
  }

  val relative = descriptor.rootPath.relativize(this)
  val (eelRoot, rest) = when (descriptor.osFamily) {
    EelOsFamily.Posix -> {
      "/" to relative
    }
    EelOsFamily.Windows -> {
      WindowsPathUtils.rootRelativeToEelPath(relative)
    }
  }
  return rest.fold(EelPath.parse(eelRoot, descriptor)) { path, part ->
    @OptIn(EelDelicateApi::class)
    localToIjent(part.toString()).takeIf { it.isNotEmpty() }?.let { path.getChild(it) } ?: path
  }
}