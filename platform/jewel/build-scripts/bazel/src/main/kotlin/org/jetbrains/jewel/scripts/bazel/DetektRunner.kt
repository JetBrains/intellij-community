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
import org.w3c.dom.Element
import org.xml.sax.InputSource

private const val JAVA_INFO_KEY = "@@rules_java+//java/private:java_info.bzl%JavaInfo"

// Run it like: bazel run //platform/jewel/build-scripts/bazel:detekt
fun main(args: Array<String>) {
    val workspaceDir = getBuildWorkspaceDirectory()
    val runfilesDir = getRunfilesDir()

    // Gradle's detekt task runs per-module with that module's own compile classpath, and rules like
    // UnusedImport require full Analysis API type resolution to fire at all (they're silently skipped
    // under light mode). To match Gradle's behavior 1:1 rather than just approximating it, we scope both
    // --input and --classpath to exactly the same set of modules Gradle's jewel-linting convention plugin
    // covers (derived from their _test_lib targets) — scanning wider than that (e.g. build-scripts/bazel or
    // detekt-plugin, neither of which are "jewel convention" modules) resolves against the wrong classpath
    // and produces bogus compiler errors that undermine the whole analysis.
    println("${"INFO: ".asWarning()}Resolving Jewel module scope and classpath for --analysis-mode full...")
    val scope = resolveJewelBazelScope(workspaceDir)

    val defaultArgs =
        arrayOf(
            "--input",
            scope.inputDirs.joinToString(if (isWindows) ";" else ":"),
            "--config",
            "$workspaceDir/platform/jewel/detekt.yml",
            "--excludes",
            "**/generated/**,**/build/**,**/buildSrc/**,**/*.kts,**/test/resources/**",
            "--build-upon-default-config",
            "--analysis-mode",
            "full",
            "--classpath",
            scope.classpath,
            "--plugins",
            listOf(
                    "$runfilesDir/_main/platform/jewel/build-scripts/bazel/detekt-plugin-lib.jar",
                    // Both compose rules are needed. The first jar has the actual rules, and it depends on
                    // types declared in the common-detekt jar.
                    "$runfilesDir/+http_file+jewel_deps_nlopez_detekt/file/nlopez-detekt.jar",
                    "$runfilesDir/+http_file+jewel_deps_nlopez_common_detekt/file/nlopez-common-detekt.jar",
                )
                .joinToString(if (isWindows) ";" else ":"),
        )

    println("${"INFO: ".asWarning()}Running detekt...")

    // Gradle forwards each module's compilerOptions.optIn to detekt as a `-opt-in` compiler arg. It's
    // undocumented in --help because it isn't a JCommander option; it passes straight through to the
    // underlying Kotlin compiler. Without it, using an opt-in-gated API (ExperimentalJewelApi, etc.) is a
    // hard compiler error, not an unresolved reference: the API resolves fine, the compiler just refuses to
    // let anything use it without permission.
    val finalArgs = defaultArgs + args + arrayOf("-opt-in", scope.optInAnnotations.joinToString(","))

    val result = DetektCli.load().run(finalArgs, System.out, System.err)

    val exitCode =
        when (val error = result.error) {
            null -> {
                println("${"SUCCESS:".asSuccess()} No issues found!")
                0
            }
            is UnexpectedError -> {
                println("${"ERROR:".asError()} An unexpected error occurred: ${error.cause.message}. Check logs.")
                1
            }
            is IssuesFound -> {
                println("${red("ERROR:")} Issues were found. Check logs and fix them.")
                2
            }
            is InvalidConfig -> {
                println("${red("ERROR:")} Invalid Detekt configuration file. Fix it and try again.")
                3
            }
        }

    exitProcess(exitCode)
}
private class JewelBazelScope(val inputDirs: List<String>, val classpath: String, val optInAnnotations: List<String>)

private fun resolveJewelBazelScope(workspaceDir: File): JewelBazelScope {
    val bazelCmd = File(workspaceDir, "bazel.cmd").absolutePath

    // The "_test_lib" suffix is a naming convention from the current JPS-to-Bazel generator, not a Jewel concept.
    // It happens to match Gradle's own module list exactly today (verified: every module applying the `jewel`
    // convention plugin has one, except detekt-plugin, which has no Bazel target at all). Once BUILD.bazel files
    // stop being generated from the JPS model and become hand-authored, replace this with an explicit per-module
    // tag instead -- that's unsafe today only because a generator rewrite would silently erase it.
    val testLibTargets =
        runBazelCommand(bazelCmd, workspaceDir, "query", """filter("_test_lib$", //platform/jewel/...)""")
            .lineSequence()
            .filter { it.startsWith("//") }
            .toList()

    check(testLibTargets.isNotEmpty()) { "Could not find any Jewel _test_lib targets to scope input/classpath from." }

    val modulePackages = testLibTargets.map { it.substringBefore(':') }.distinct().sorted()
    val inputDirs = modulePackages.map { "$workspaceDir/${it.removePrefix("//")}" }

    // query/cquery only analyze the build graph, they never execute build actions themselves. Building just the
    // _test_lib targets isn't enough either since Bazel only materializes a directly-requested target's own default
    // outputs, and _test_lib wires its module in via runtime_deps - an edge Bazel does NOT build merely because
    // the requesting target was built. Building the whole platform/jewel tree directly requests every module's own
    // target too, forcing their outputs to exist and be current. It's a single  wildcard pattern (no argv-length
    // risk from an explicit target list, unlike e.g. `deps(...)`, which for this tree expands to tens of thousands
    // of individual labels) and a near-instant no-op when already warm.
    println("${"INFO: ".asWarning()}Building platform/jewel before assembling classpath...")
    runBazelCommand(bazelCmd, workspaceDir, "build", "//platform/jewel/...")

    // create_kotlinc_options(opt_in = [...]) is exposed as a structured "opt_in" attribute on the
    // "*_rules_kotlin_options" target the kt_kotlinc_options macro creates for each module, so it's read via
    // `bazel query --output=xml` rather than regexing BUILD.bazel text. This also correctly picks up the
    // macro's own default opt-in list for any module that ever omits opt_in explicitly, which a text-only scan
    // over BUILD.bazel could never see.
    val optInQuery = """kind("kt_kotlinc_options", ${modulePackages.joinToString(" + ") { "$it:*" }})"""
    val optInXml = runBazelCommand(bazelCmd, workspaceDir, "query", optInQuery, "--output=xml")
    val optInAnnotations = parseOptInAnnotations(optInXml)

    check(optInAnnotations.isNotEmpty()) {
        "Could not find any opt_in annotations in the scoped modules' kt_kotlinc_options targets."
    }

    // "external/..." paths (fetched deps) live under output_base itself; only repos touched by the most
    // recent build get a same-named symlink under execution_root's own "external/", so that prefix can't be
    // relied on. "bazel-out/..." paths (generated files, e.g. .abi.jar) live under execution_root instead.
    val outputBase = runBazelCommand(bazelCmd, workspaceDir, "info", "output_base").trim()
    val executionRoot = runBazelCommand(bazelCmd, workspaceDir, "info", "execution_root").trim()

    // Deliberately transitive_runtime_jars, not transitive_compile_time_jars: _test_lib targets wire their
    // own module in via runtime_deps (not deps), so compile_time_jars misses everything reachable only
    // through that edge (e.g. gfm-strikethrough's commonmark-ext-gfm-strikethrough dep). runtime_jars is a
    // strict superset that's what we actually want here — full visibility for type resolution, not the
    // stricter compile/runtime isolation Bazel enforces for the build graph itself.
    val starlarkExpr =
        """"\n".join([f.path for f in providers(target)["$JAVA_INFO_KEY"].transitive_runtime_jars.to_list()])"""

    val jarPaths =
        runBazelCommand(
                bazelCmd,
                workspaceDir,
                "cquery",
                testLibTargets.joinToString(" + "),
                "--output=starlark",
                "--starlark:expr=$starlarkExpr",
            )
            .lineSequence()
            .map { it.trim() }
            .filter { it.endsWith(".jar") }
            .map { if (it.startsWith("external/")) "$outputBase/$it" else "$executionRoot/$it" }
            .distinct()
            .toList()

    check(jarPaths.isNotEmpty()) { "Resolved an empty classpath from Bazel; something's wrong with the query." }

    return JewelBazelScope(inputDirs, jarPaths.joinToString(if (isWindows) ";" else ":"), optInAnnotations)
}

private fun parseOptInAnnotations(xml: String): List<String> {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
    val lists = document.getElementsByTagName("list")
    return (0 until lists.length)
        .asSequence()
        .map { lists.item(it) as Element }
        .filter { it.getAttribute("name") == "opt_in" }
        .flatMap { listElement ->
            val strings = listElement.getElementsByTagName("string")
            (0 until strings.length).map { (strings.item(it) as Element).getAttribute("value") }
        }
        .distinct()
        .sorted()
        .toList()
}

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
