@file:Suppress("IO_FILE_USAGE", "CompletableFuture.defaultExecutor")

package org.jetbrains.jewel.scripts.bazel

import dev.detekt.tooling.api.DetektCli
import dev.detekt.tooling.api.InvalidConfig
import dev.detekt.tooling.api.IssuesFound
import dev.detekt.tooling.api.UnexpectedError
import java.io.File
import java.io.StringReader
import java.util.concurrent.CompletableFuture
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.exitProcess
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource

private const val JAVA_INFO_KEY = "@@rules_java+//java/private:java_info.bzl%JavaInfo"

// The attribute of a `kt_kotlinc_options` target that holds the opt-in annotations.
private const val OPT_IN_ATTRIBUTE = "x_optin"

// `.idea/modules.xml` starts the `filepath` attribute of each module with this prefix.
private const val PROJECT_DIR_PREFIX = "\$PROJECT_DIR\$/"

private const val JEWEL_DIR = "platform/jewel/"

// Run it like: bazel run //platform/jewel/build-scripts/bazel:detekt
fun main(args: Array<String>) {
    val workspaceDir = getBuildWorkspaceDirectory()

    // Gradle's detekt task runs per-module with that module's own compile classpath, and rules like
    // UnusedImport require full Analysis API type resolution to fire at all (they're silently skipped
    // under light mode). To match Gradle's behavior 1:1 rather than just approximating it, we scope both
    // --input and --classpath to exactly the same set of modules Gradle's jewel-linting convention plugin
    // covers (the JPS modules under platform/jewel). Scanning wider than that (e.g. build-scripts/bazel or
    // detekt-plugin, neither of which are "jewel convention" modules) resolves against the wrong classpath
    // and produces bogus compiler errors that undermine the whole analysis.
    println("${"INFO: ".asWarning()}Resolving Jewel module scope and classpath for --analysis-mode full...")
    val scope = resolveJewelBazelScope(workspaceDir)

    println("${"INFO: ".asWarning()}Running detekt...")

    val finalArgs = buildDetektArgs(workspaceDir, scope, args)

    val result = DetektCli.load().run(finalArgs, System.out, System.err)

    val exitCode =
        when (val error = result.error) {
            null -> {
                printlnSuccess("SUCCESS: No issues found!")
                0
            }
            is UnexpectedError -> {
                printlnErr("ERROR: An unexpected error occurred: ${error.cause.message}. Check logs.")
                1
            }
            is IssuesFound -> {
                printlnErr("ERROR: Issues were found. Check logs and fix them.")
                2
            }
            is InvalidConfig -> {
                printlnErr("ERROR: Invalid Detekt configuration file. Fix it and try again.")
                3
            }
        }

    exitProcess(exitCode)
}

// Gradle forwards each module's compilerOptions.optIn to detekt as a `-opt-in` compiler arg. It's
// undocumented in --help because it isn't a JCommander option; it passes straight through to the
// underlying Kotlin compiler. Without it, using an opt-in-gated API (ExperimentalJewelApi, etc.) is a
// hard compiler error, not an unresolved reference: the API resolves fine, the compiler just refuses to
// let anything use it without permission.
internal fun buildDetektArgs(workspaceDir: File, scope: JewelBazelScope, passthroughArgs: Array<String>): Array<String> {
    val defaultArgs =
        arrayOf(
            "--input",
            scope.inputDirs.joinToString(File.pathSeparator),
            "--config",
            "$workspaceDir/platform/jewel/detekt.yml",
            "--excludes",
            "**/generated/**,**/build/**,**/buildSrc/**,**/*.kts,**/test/resources/**",
            "--build-upon-default-config",
            "--analysis-mode",
            "full",
            "--classpath",
            scope.classpath,
        )
    return defaultArgs + passthroughArgs + arrayOf("-opt-in", scope.optInAnnotations.joinToString(","))
}

internal class JewelBazelScope(val inputDirs: List<String>, val classpath: String, val optInAnnotations: List<String>)

private fun resolveJewelBazelScope(workspaceDir: File): JewelBazelScope {
    val bazelCmd = File(workspaceDir, "bazel.cmd").absolutePath

    // `.idea/modules.xml` lists the JPS modules, and Detekt checks the ones under platform/jewel. The file is part
    // of the JPS model, like the `jps_module` records. If the monorepo drops that model, this list needs another
    // source.
    val moduleDirs = parseJewelModuleDirs(File(workspaceDir, ".idea/modules.xml").readText())

    check(moduleDirs.isNotEmpty()) { "Could not find any Jewel module in .idea/modules.xml." }

    val inputDirs = moduleDirs.map { "$workspaceDir/$it" }

    // Each module folder is a Bazel package. Detekt reads every Kotlin file in the folder, so the classpath comes
    // from every library in the package. Some of them are outside the JPS model, for example the Spectre tests.
    val modulePackageTargets = moduleDirs.joinToString(" + ") { "//$it:*" }
    val libraryTargets = queryTargets(bazelCmd, workspaceDir, """kind("jvm_library", $modulePackageTargets)""")

    // query and cquery only read the build graph, so the script builds the platform/jewel tree first. The jars of
    // the classpath then exist and are current. One wildcard pattern keeps the command line short, and a warm
    // build takes almost no time. The build skips build-scripts, because that folder has no JPS module and adds
    // nothing to the classpath.
    println("${"INFO: ".asWarning()}Building platform/jewel before assembling classpath...")
    runBazelCommand(
        bazelCmd,
        workspaceDir,
        "build",
        "--",
        "//platform/jewel/...",
        "-//platform/jewel/build-scripts/...",
    )

    // create_kotlinc_options(opt_in = [...]) is exposed as a structured "x_optin" attribute on the
    // "*_rules_kotlin_options" target the kt_kotlinc_options macro creates for each module, so it's read via
    // `bazel query --output=xml` rather than regexing BUILD.bazel text. This also correctly picks up the
    // macro's own default opt-in list for any module that ever omits opt_in explicitly, which a text-only scan
    // over BUILD.bazel could never see.
    val optInQuery = """kind("kt_kotlinc_options", $modulePackageTargets)"""
    val optInXml = runBazelCommand(bazelCmd, workspaceDir, "query", optInQuery, "--output=xml")
    val optInAnnotations = parseOptInAnnotations(optInXml)

    check(optInAnnotations.isNotEmpty()) {
        "Could not find any $OPT_IN_ATTRIBUTE annotations in the scoped modules' kt_kotlinc_options targets."
    }

    // "external/..." paths (fetched deps) live under output_base itself; only repos touched by the most
    // recent build get a same-named symlink under execution_root's own "external/", so that prefix can't be
    // relied on. "bazel-out/..." paths (generated files, e.g. .abi.jar) live under execution_root instead.
    val infoByKey =
        runBazelCommand(bazelCmd, workspaceDir, "info", "output_base", "execution_root")
            .lineSequence()
            .mapNotNull { line -> line.indexOf(": ").takeIf { it != -1 }?.let { line.substring(0, it) to line.substring(it + 2) } }
            .toMap()
    val outputBase = infoByKey.getValue("output_base")
    val executionRoot = infoByKey.getValue("execution_root")

    // The classpath uses transitive_runtime_jars. The other list, transitive_compile_time_jars, leaves out what a
    // library reaches only through runtime_deps, and Detekt needs the full classpath to resolve types.
    val starlarkExpr =
        """"\n".join([f.path for f in providers(target)["$JAVA_INFO_KEY"].transitive_runtime_jars.to_list()])"""

    val jarPaths =
        runBazelCommand(
                bazelCmd,
                workspaceDir,
                "cquery",
                libraryTargets.joinToString(" + "),
                "--output=starlark",
                "--starlark:expr=$starlarkExpr",
            )
            .lineSequence()
            .map { it.trim() }
            .filter { it.endsWith(".jar") }
            .map { if (it.startsWith("external/")) "$outputBase/$it" else "$executionRoot/$it" }
            .distinct()
            .filter { File(it).exists() }
            .toList()

    check(jarPaths.isNotEmpty()) { "Resolved an empty classpath from Bazel; something's wrong with the query." }

    return JewelBazelScope(inputDirs, jarPaths.joinToString(File.pathSeparator), optInAnnotations)
}

/**
 * Returns the folders of the JPS modules under platform/jewel, as paths from the workspace root.
 *
 * @param modulesXml The content of `.idea/modules.xml`. It registers each JPS module with the path of its `.iml` file.
 */
internal fun parseJewelModuleDirs(modulesXml: String): List<String> {
    val modules = parseXml(modulesXml).getElementsByTagName("module")
    return (0 until modules.length)
        .asSequence()
        .map { (modules.item(it) as Element).getAttribute("filepath").removePrefix(PROJECT_DIR_PREFIX) }
        .filter { it.startsWith(JEWEL_DIR) }
        .map { it.substringBeforeLast('/') }
        .distinct()
        .sorted()
        .toList()
}

internal fun parseOptInAnnotations(xml: String): List<String> {
    val lists = parseXml(xml).getElementsByTagName("list")
    return (0 until lists.length)
        .asSequence()
        .map { lists.item(it) as Element }
        .filter { it.getAttribute("name") == OPT_IN_ATTRIBUTE }
        .flatMap { listElement ->
            val strings = listElement.getElementsByTagName("string")
            (0 until strings.length).map { (strings.item(it) as Element).getAttribute("value") }
        }
        .distinct()
        .sorted()
        .toList()
}

private fun parseXml(xml: String): Document =
    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))

private fun queryTargets(bazelCmd: String, workspaceDir: File, query: String): List<String> =
    runBazelCommand(bazelCmd, workspaceDir, "query", query).lineSequence().filter { it.startsWith("//") }.toList()

private fun runBazelCommand(bazelCmd: String, workspaceDir: File, vararg args: String): String {
    // Bazel writes progress noise (INFO:, Loading:, Analyzing:, ...) to stderr and the actual command result
    // to stdout, so the two must NOT be merged or the result gets corrupted by that noise. Both streams are
    // drained concurrently on separate threads so a large amount of output on one can't deadlock the other.
    val process = ProcessBuilder(listOf(bazelCmd, *args)).directory(workspaceDir).start()
    val stdoutFuture = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
    val stderrFuture = CompletableFuture.supplyAsync { process.errorStream.bufferedReader().readText() }
    val exitCode = process.waitFor()
    val stdout = stdoutFuture.get()
    val stderr = stderrFuture.get()
    check(exitCode == 0) { "Command '${args.joinToString(" ")}' failed with exit code $exitCode:\n$stderr" }
    return stdout
}
