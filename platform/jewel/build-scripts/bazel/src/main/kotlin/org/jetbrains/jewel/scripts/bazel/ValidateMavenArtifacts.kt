@file:Suppress("RAW_RUN_BLOCKING", "SSBasedInspection")
package org.jetbrains.jewel.scripts.bazel

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.terminal.YesNoPrompt
import java.io.File
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

// Run this on CLI like: bazel run //platform/jewel/build-scripts/bazel:validateMavenArtifacts -- branch1 branch2
fun main(args: Array<String>) {
    runBlocking { ValidateMavenArtifactsCommand().main(args) }
}

internal class ValidateMavenArtifactsCommand(
    private val commandRunner: CommandRunner = DefaultCommandRunner,
    private val isDirectory: (File) -> Boolean = File::isDirectory,
    private val isFile: (File) -> Boolean = File::isFile,
) : SuspendingCliktCommand() {
    private val branches: List<String> by
        argument(help = "Branches to build and compare artifacts from (at least two).").multiple(required = true)

    private val forcePull: Boolean by
        option("--force-pull", help = "Force pull untracked branches before building.").flag(default = false)

    private val verbose: Boolean by option("--verbose", "-v", help = "Enable verbose output.").flag(default = false)

    private val artifactsDir: File? by
        option(
                "--artifacts-dir",
                "-a",
                "--out-dir",
                "-o",
                help =
                    "Directory to store the built artifacts. " +
                        "If not specified, it will create a temp dir in the system temp dir.",
            )
            .file(mustExist = false, canBeFile = false)

    private val preserveTemp: Boolean by
        option(
                "--preserve-temp",
                "-p",
                help = "Do not clean up the temporary directory after execution. Implicit when --no-build is specified.",
            )
            .flag(default = false)

    private val noBuild: Boolean by
        option(
                "--no-build",
                "-n",
                help =
                    "Skip building artifacts, only validate the artifacts in --artifacts-dir. " +
                        "Requires passing --artifacts-dir, implies --preserve-temp.",
            )
            .flag(default = false)

    private val mavenArtifactsOutputDirName = "maven-artifacts" // Matches JewelMavenArtifactsBuildTarget

    override fun help(context: Context): String = "Validates Jewel Maven artifacts across specified branches."

    override suspend fun run() =
        withContext(Dispatchers.IO) {
            if (branches.size < 2) {
                exitWithError("Please specify at least two branches to compare.")
            }

            print("⏳ Locating IntelliJ Community root...")
            val communityRoot = getBuildWorkspaceDirectory()

            println(" DONE: ${communityRoot.canonicalPath}")

            print("⏳ Checking git status...")
            if (!isDirectoryGitRepo(communityRoot, commandRunner)) {
                println()
                exitWithError("Not a git repository at ${communityRoot.canonicalPath}.")
            }

            // Check if the repo has pending changes, which can cause issues when switching branches.
            // If --no-build is specified, we don't switch branches to build, so this is not needed.
            if (!noBuild) {
                val gitStatus = commandRunner("git status --porcelain", communityRoot, exitOnError = true).output.trim()
                if (gitStatus.isNotEmpty()) {
                    println()
                    exitWithError(
                        "Local changes detected in the git repository at ${communityRoot.canonicalPath}. " +
                            "Please commit or stash them."
                    )
                }
            }

            // Store the original branch to return to later
            val originalBranch = getCurrentBranchName(communityRoot, commandRunner)
            printlnSuccess(" DONE")

            if (verbose) println("  Original branch: $originalBranch")

            val mavenArtifactsDir = communityRoot.resolve("out/idea-ce/artifacts/$mavenArtifactsOutputDirName")
            val artifactsDirectory = artifactsDir ?: Files.createTempDirectory("jewel-artifacts").toFile()

            if (noBuild) {
                if (artifactsDirectory == null || !isDirectory(artifactsDirectory)) {
                    exitWithError(
                        "When --no-build is specified, --artifacts-dir must be " +
                            "provided and point to an existing directory."
                    )
                }

                println("ℹ️ Skipping build and validating from ${artifactsDirectory.canonicalPath}")

                val hasDiscrepancies = validateMavenArtifacts(artifactsDirectory)
                if (!hasDiscrepancies) {
                    println("✅ No discrepancies found in artifact presence or dependencies.")
                } else {
                    exitWithError("❌ Discrepancies found. Please review the output.")
                }
            } else {
                validateAndBuildArtifacts(mavenArtifactsDir, artifactsDirectory, communityRoot, originalBranch)

                if (!preserveTemp && isDirectory(artifactsDirectory) && artifactsDir == null) {
                    println("🧹 Cleaning temporary artifacts directory: ${artifactsDirectory.canonicalPath}")
                    artifactsDirectory.deleteRecursively()
                }
            }
        }

    private suspend fun validateAndBuildArtifacts(
        mavenArtifactsDir: File,
        artifactsRoot: File,
        communityRoot: File,
        originalBranch: String,
    ) {
        try {
            checkJewelMavenArtifactsFileConsistency(communityRoot)

            print("🧹 Cleaning output directory: $mavenArtifactsDir...")
            if (isDirectory(mavenArtifactsDir)) {
                mavenArtifactsDir.deleteRecursively()
            }
            mavenArtifactsDir.mkdirs()
            printlnSuccess(" DONE")

            if (isDirectory(artifactsRoot)) {
                print("🧹 Cleaning artifacts directory: $artifactsRoot...")
                artifactsRoot.deleteRecursively()
                printlnSuccess(" DONE")
            }
            artifactsRoot.mkdirs()

            for (branch in branches) {
                println("\n--- Building artifacts for branch ${branch.asBold()} ---")
                checkoutBranch(branch, communityRoot)

                val buildTargetFile = communityRoot.resolve("build/src/JewelMavenArtifactsBuildTarget.kt")
                if (!isFile(buildTargetFile)) {
                    exitWithError("Build target file not found at ${buildTargetFile.canonicalPath}")
                }

                println("🩹 Patching ${buildTargetFile.relativeTo(communityRoot).toString().asBold()}")
                val patchedBuildTargetContent =
                    buildTargetFile
                        .readText()
                        .replace("projectHome = ULTIMATE_HOME", "projectHome = COMMUNITY_ROOT.communityRoot")
                buildTargetFile.writeText(patchedBuildTargetContent)

                pullBranchIfNeeded(branch, communityRoot)

                buildArtifactsOnBranch(communityRoot, branch)

                // Copy artifacts to the temp directory
                println("📦 Copying artifacts for branch ${branch.asBold()} to ${artifactsRoot.canonicalPath}")
                mavenArtifactsDir.copyRecursively(artifactsRoot, true)
            }

            println()

            val hasDiscrepancies = validateMavenArtifacts(artifactsRoot)
            if (!hasDiscrepancies) {
                println("✅ No discrepancies found in artifact presence or dependencies.")
            } else {
                exitWithError("❌ Discrepancies found. Please review the output.")
            }
        } finally {
            cleanup(communityRoot, originalBranch, artifactsRoot, preserveTemp)
        }
    }

    private suspend fun checkJewelMavenArtifactsFileConsistency(communityRoot: File) {
        println("\n🔍 Verifying JewelMavenArtifacts.kt consistency across branches...")

        val filePath = "build/src/org/jetbrains/intellij/build/JewelMavenArtifacts.kt"
        val contentsByBranch =
            branches.associateWith { branch ->
                println("    Checking branch $branch...")
                commandRunner("git show $branch:$filePath", communityRoot, exitOnError = false)
            }

        val failedBranches = contentsByBranch.filterValues { it.isFailure }.map { it.key }
        if (failedBranches.isNotEmpty()) {
            exitWithError("Could not read $filePath from branches: ${failedBranches.joinToString()}.")
        }

        val contents = contentsByBranch.mapValues { it.value.getOrThrow() }
        val referenceBranch = branches.first()
        val referenceContent = contents[referenceBranch]

        val differingBranches = branches.drop(1).filter { contents[it] != referenceContent }

        if (differingBranches.isEmpty()) {
            printlnSuccess("✅ JewelMavenArtifacts.kt is consistent across all branches.")
            return
        }

        printlnWarn(
            "Found differences in build/src/org/jetbrains/intellij/build/JewelMavenArtifacts.kt between branches."
        )
        printlnWarn("This is likely to produce inconsistent build results.")

        for (branch in differingBranches) {
            println("\n--- Diff between ${referenceBranch.asBold()} and ${branch.asBold()} ---")
            val diffResult =
                commandRunner("git diff $referenceBranch $branch -- $filePath", communityRoot, exitOnError = false)
            if (diffResult.isSuccess && diffResult.output.isNotBlank()) {
                println(diffResult.output)
            } else {
                printlnErr("Could not compute diff for $filePath between branches '$referenceBranch' and '$branch'.")
            }
        }

        if (YesNoPrompt("Do you want to proceed with the build anyway?", terminal, default = true).ask() == false) {
            exitWithError("Build cancelled by user due to file inconsistencies.")
        }
    }

    private suspend fun checkoutBranch(branch: String, communityRoot: File) {
        print("Checking out branch '$branch'...")
        val checkoutResult = commandRunner("git checkout $branch", communityRoot)
        if (checkoutResult.isFailure) {
            // Attempt to stash and retry checkout
            printlnWarn("Checkout failed, attempting to stash changes and retry...")
            commandRunner("git stash", communityRoot, exitOnError = false)
            val retryCheckoutResult = commandRunner("git checkout $branch", communityRoot)
            if (retryCheckoutResult.isFailure) {
                println()
                exitWithError("Failed to checkout branch '$branch' even after stashing. Aborting.")
            } else {
                println("  Checkout successful after stashing.")
            }
        } else {
            printlnSuccess(" DONE")
        }
    }

    private suspend fun pullBranchIfNeeded(branch: String, communityRoot: File) {
        val remoteInfo =
            commandRunner(
                    command = "git for-each-ref --format='%(upstream:remotename)' refs/heads/$branch",
                    workingDir = communityRoot,
                    exitOnError = false,
                )
                .output
                .trim()

        val trackingBranch =
            commandRunner(
                    command = "git for-each-ref --format='%(upstream:short)' refs/heads/$branch",
                    workingDir = communityRoot,
                    exitOnError = false,
                )
                .output
                .trim()

        if (remoteInfo.isNotEmpty() && trackingBranch.isNotEmpty()) {
            val pullNeeded =
                commandRunner("git status -uno", communityRoot, exitOnError = false).output.let {
                    it.contains("Your branch is behind") || it.contains("Your branch and") // Covers divergence
                }

            if (pullNeeded || forcePull) {
                pullBranch(branch, trackingBranch, communityRoot)
            } else if (verbose) {
                println("Branch '$branch' is up-to-date or doesn't need pulling.")
            }
        } else if (verbose) {
            println("Branch '$branch' does not track a remote branch.")
        }
    }

    private suspend fun pullBranch(branch: String, trackingBranch: String, communityRoot: File) {
        val shouldPull =
            if (forcePull) {
                true
            } else {
                print("Branch '$branch' tracks '$trackingBranch'. Do you want to pull? (y/n): ")
                readlnOrNull()?.trim()?.lowercase() == "y"
            }

        if (shouldPull) {
            println("Pulling from '$trackingBranch'...")
            val pullResult = commandRunner("git pull --rebase", communityRoot, exitOnError = false)

            if (pullResult.isFailure) {
                val statusAfterPull = commandRunner("git status", communityRoot).output
                if (
                    statusAfterPull.contains("Unmerged paths") || statusAfterPull.contains("You have unstaged changes")
                ) {
                    printlnErr("Conflicts detected after pull. Please resolve manually and re-run the script.")
                }
                exitWithError("Failed to pull branch '$branch'. Aborting...")
            }
            println("Pull successful.")
        } else {
            println("Skipping pull for branch '$branch'.")
        }
    }

    private suspend fun buildArtifactsOnBranch(communityRoot: File, branch: String) {
        println("🚀 Building artifacts...")

        val buildCommand = buildString {
            append("platform/jps-bootstrap/jps-bootstrap")
            append(if (isWindows) ".cmd" else ".sh")
            append(" ")
            append(communityRoot.absolutePath)
            append(" intellij.idea.community.build JewelMavenArtifactsBuildTarget")
        }

        if (verbose) println("Build command: $buildCommand")

        val buildResult =
            commandRunner(
                buildCommand,
                communityRoot,
                timeoutAmount = 30.minutes, // Allow more time for build
                streamOutput = verbose,
            )

        if (buildResult.isFailure) {
            println(buildResult.output)
            exitWithError("Build failed for branch '$branch'. Aborting...")
        }
        println("Build successful for branch ${branch.asBold()}.")
    }

    private fun validateMavenArtifacts(artifactsDir: File): Boolean {
        println("\n--- Comparing built artifacts ---")

        val pomFiles =
            artifactsDir
                .walkTopDown()
                .onFail { file, ioException -> printlnErr("Error accessing $file: $ioException") }
                .filter { it.extension.lowercase() == "pom" }
                .toList()

        if (verbose) {
            println("Found ${pomFiles.size} POM files. Computing distinct groupId:artifactId coordinates...")
        }

        if (pomFiles.isEmpty()) {
            exitWithError("No POM files found in the temporary output directory.")
        }

        val artifactNames = pomFiles.map { getArtifactNameFromPom(it) }.toSortedSet()

        println("Found ${artifactNames.size} unique artifacts.")

        if (verbose) {
            println("Artifact coordinates:")
            artifactNames.sorted().forEach { println("  - $it") }
        }

        return checkForDiscrepancies(artifactNames, pomFiles)
    }

    private fun checkForDiscrepancies(allFoundArtifactNames: MutableSet<String>, pomFiles: List<File>): Boolean {
        println("\nVerifying artifact presence across branches...")

        var hasDiscrepancies = false

        // First, check that we have all versions for each artifact
        for (artifactName in allFoundArtifactNames) {
            val pomsForArtifact = pomFiles.filter { pomFile -> getArtifactNameFromPom(pomFile) == artifactName }

            if (pomsForArtifact.size != branches.size) {
                printlnErr(
                    buildString {
                        append("  * Artifact ")
                        append(artifactName.asBold())
                        append(" is missing in one or more branches.".asError())
                        append(" (")
                        append(branches.size)
                        append(" expected, ")
                        append(pomsForArtifact.size)
                        append(" found).")
                    }
                )
                printlnErr("    Found versions:")
                pomsForArtifact.forEach { println("     - " + getVersionFromPom(it).asBold()) }
                hasDiscrepancies = true
            }
        }

        // Then, compare dependencies for each artifact across versions
        return checkForDependencyDiscrepancies(allFoundArtifactNames, pomFiles) || hasDiscrepancies
    }

    private fun checkForDependencyDiscrepancies(artifactNames: MutableSet<String>, pomFiles: List<File>): Boolean {
        println("\nVerifying dependencies across branches...")

        var hasDiscrepancies = false
        for (artifactName in artifactNames) {
            val pomsForArtifact = pomFiles.filter { pomFile -> getArtifactNameFromPom(pomFile) == artifactName }

            if (pomsForArtifact.size < 2) {
                printlnWarn(
                    "Skipping dependency comparison for ${artifactName.asBold()}: " +
                        "only ${pomsForArtifact.size} POMs found."
                )
                continue
            }

            val dependencySets = pomsForArtifact.associateWith { pomFile -> getDependenciesFromPom(pomFile) }

            hasDiscrepancies =
                crossValidateDependencies(pomsForArtifact, dependencySets, artifactName) || hasDiscrepancies
        }
        return hasDiscrepancies
    }

    internal fun getArtifactNameFromPom(pomFile: File): String =
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(pomFile)
            doc.documentElement.normalize()

            val groupId =
                checkNotNull(doc.getElementsByTagName("groupId").item(0).textContent) { "groupId not found" }.trim()
            val artifactId =
                checkNotNull(doc.getElementsByTagName("artifactId").item(0).textContent) { "artifactId not found" }
                    .trim()

            "$groupId:$artifactId"
        } catch (e: Exception) {
            exitWithError("Error parsing POM file ${pomFile.name}: ${e.message}")
        }

    internal fun getDependenciesFromPom(pomFile: File): Set<String> {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(pomFile)
            doc.documentElement.normalize()

            val dependencies = mutableSetOf<String>()
            val dependencyNodes: NodeList = doc.getElementsByTagName("dependency")

            for (i in 0 until dependencyNodes.length) {
                val dependencyNode: Node = dependencyNodes.item(i)
                val coordinates = processDependencyNode(dependencyNode, pomFile.name, i)
                if (coordinates != null) dependencies.add(coordinates)
            }
            dependencies
        } catch (e: Exception) {
            printlnErr("Error parsing dependencies in POM file ${pomFile.name}: ${e.message}")
            emptySet()
        }
    }

    private fun processDependencyNode(dependencyNode: Node, fileName: String, position: Int): String? {
        if (dependencyNode.nodeType != Node.ELEMENT_NODE) return null
        val element = dependencyNode as Element
        val groupId = element.getElementsByTagName("groupId").item(0)?.textContent?.trim()
        val artifactId = element.getElementsByTagName("artifactId").item(0)?.textContent?.trim()

        return if (groupId != null && artifactId != null) {
            "$groupId:$artifactId"
        } else {
            printlnWarn("Skipping incomplete dependency in $fileName at position #$position")
            null
        }
    }

    internal fun crossValidateDependencies(
        poms: List<File>,
        dependencySets: Map<File, Set<String>>,
        artifactName: String,
    ): Boolean {
        var hasDiscrepancies = false

        for ((i, element) in poms.withIndex()) {
            for (j in i + 1 until poms.size) {
                val file1 = element
                val file2 = poms[j]
                val deps1 = dependencySets[file1] ?: emptySet()
                val deps2 = dependencySets[file2] ?: emptySet()

                hasDiscrepancies =
                    crossValidateDependencies(artifactName, file1, file2, deps1, deps2) || hasDiscrepancies
            }
        }

        println()

        return hasDiscrepancies
    }

    internal fun crossValidateDependencies(
        artifactName: String,
        file1: File,
        file2: File,
        deps1: Set<String>,
        deps2: Set<String>,
    ): Boolean {
        var hasDiscrepancies = false
        val discrepancies = (deps1 union deps2) - (deps1 intersect deps2)

        if (discrepancies.isNotEmpty()) {
            printlnErr(
                buildString {
                    append("  * Dependencies DIFFER for ")
                    append(artifactName.asBold())
                    append(" between ")
                    append(file1.name.asBold().asLink(file1.absolutePath))
                    append(" and ")
                    append(file2.name.asBold().asLink(file2.absolutePath))
                    append(".")
                }
            )

            println("    Unique dependencies:")
            discrepancies.sorted().forEach { println("      - ${it.asBold()}") }
            hasDiscrepancies = true
        } else {
            printlnSuccess(
                buildString {
                    append("  * Dependencies MATCH for ")
                    append(artifactName.asBold())
                    append(" between ")
                    append(file1.name.asBold().asLink(file1.absolutePath))
                    append(" and ")
                    append(file2.name.asBold().asLink(file2.absolutePath))
                    append(".")
                }
            )
        }

        return hasDiscrepancies
    }

    internal suspend fun cleanup(
        communityRoot: File,
        originalBranch: String,
        artifactsRoot: File,
        preserveTemp: Boolean,
    ) {
        println("\n--- Cleaning up ---")

        print("🔄 Reverting local changes...")
        commandRunner("git reset --hard", communityRoot)
        printlnSuccess(" DONE")

        print("🔄 Returning to original branch '$originalBranch'...")
        commandRunner("git checkout $originalBranch", communityRoot)
        printlnSuccess(" DONE")

        if (!preserveTemp) {
            println("🧹 Cleaning artifacts directory: ${artifactsRoot.canonicalPath}")
            if (isDirectory(artifactsRoot)) {
                artifactsRoot.deleteRecursively()
            }
        } else {
            println("ℹ️ Skipping cleanup of artifacts directory: ${artifactsRoot.canonicalPath}")
        }
    }

    internal fun getVersionFromPom(pomFile: File): String =
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(pomFile)
            doc.documentElement.normalize()

            checkNotNull(doc.getElementsByTagName("version").item(0)?.textContent?.trim()) { "version not found" }
        } catch (e: Exception) {
            exitWithError("Error parsing POM file ${pomFile.name}: ${e.message}")
        }
}
