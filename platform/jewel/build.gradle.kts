import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.composeDesktop) apply false
    alias(libs.plugins.compose.compiler) apply false
    `jewel-linting`
}

dependencies {
    sarif(projects.decoratedWindow)
    sarif(projects.foundation)
    sarif(projects.ideLafBridge)
    sarif(projects.intUi.intUiDecoratedWindow)
    sarif(projects.intUi.intUiStandalone)
    sarif(projects.markdown.core)
    sarif(projects.markdown.extensions.autolink)
    sarif(projects.markdown.extensions.gfmAlerts)
    sarif(projects.markdown.extensions.gfmStrikethrough)
    sarif(projects.markdown.extensions.gfmTables)
    sarif(projects.markdown.ideLafBridgeStyling)
    sarif(projects.markdown.intUiStandaloneStyling)
    sarif(projects.samples.idePlugin)
    sarif(projects.samples.standalone)
    sarif(projects.ui)
}

// Faff needed to comply with Gradle's service injection.
// See https://docs.gradle.org/current/userguide/service_injection.html#execoperations
interface InjectedExecOps {
    @get:Inject val execOps: ExecOperations

    fun exec(init: ExecSpec.() -> Unit): ExecResult = execOps.exec(init)
}

tasks {
    register("tagRelease") {
        val injected = project.objects.newInstance<InjectedExecOps>()

        description = "Tags main branch and releases branches with provided tag name"
        group = "release"

        doFirst {
            val rawReleaseVersion =
                ((project.property("jewel.release.version") as String?)?.takeIf { it.isNotBlank() }
                    ?: throw GradleException("Please provide a jewel.release.version in gradle.properties"))

            val releaseName = "v$rawReleaseVersion"

            val stdOut = ByteArrayOutputStream()

            // Check we're on the main branch
            logger.info("Checking current branch is main...")
            injected
                .exec {
                    commandLine = listOf("git", "rev-parse", "--abbrev-ref", "HEAD")
                    standardOutput = stdOut
                }
                .assertNormalExitValue()

            val currentBranch = stdOut.use { it.toString() }.trim()
            if (currentBranch != "main") {
                throw GradleException("This task must only be run on the main branch")
            }

            // Check tag doesn't already exist
            logger.info("Checking current branch is main...")
            stdOut.reset()
            injected
                .exec {
                    commandLine = listOf("git", "tag")
                    standardOutput = stdOut
                }
                .assertNormalExitValue()

            if (stdOut.toString().trim().lines().any { it == releaseName }) {
                throw GradleException("The tag $releaseName already exists!")
            }

            // Check there are no uncommitted changes
            logger.info("Checking all changes have been committed...")
            stdOut.reset()
            injected
                .exec {
                    commandLine = listOf("git", "status", "--porcelain")
                    standardOutput = stdOut
                }
                .assertNormalExitValue()

            if (stdOut.toString().isNotBlank()) {
                throw GradleException("Please commit all changes before tagging a release")
            }

            // Get the current HEAD hash
            logger.info("Getting HEAD hash...")
            stdOut.reset()
            injected
                .exec {
                    commandLine = listOf("git", "rev-parse", "HEAD")
                    standardOutput = stdOut
                }
                .assertNormalExitValue()

            val currentHead = stdOut.use { it.toString() }.trim()

            // Enumerate the release branches
            logger.info("Enumerating release branches...")
            stdOut.reset()
            injected
                .exec {
                    commandLine = listOf("git", "branch")
                    standardOutput = stdOut
                }
                .assertNormalExitValue()

            val releaseBranches =
                stdOut
                    .use { it.toString() }
                    .lines()
                    .filter { it.trim().startsWith("releases/") }
                    .map { it.trim().removePrefix("releases/") }

            if (releaseBranches.isEmpty()) {
                throw GradleException("No local release branches found, make sure they exist locally")
            }

            logger.lifecycle("Release branches: ${releaseBranches.joinToString { "releases/$it" }}")

            // Check all release branches have gotten the latest from main
            logger.info("Validating release branches...")
            for (branch in releaseBranches) {
                stdOut.reset()
                injected
                    .exec {
                        commandLine = listOf("git", "merge-base", "main", "releases/$branch")
                        standardOutput = stdOut
                    }
                    .assertNormalExitValue()

                val mergeBase = stdOut.use { it.toString() }.trim()
                if (mergeBase != currentHead) {
                    throw GradleException("Branch releases/$branch is not up-to-date with main!")
                }
            }

            // Tag main branch
            logger.lifecycle("Tagging head of main branch as $releaseName...")
            injected.exec { commandLine = listOf("git", "tag", releaseName) }.assertNormalExitValue()

            // Tag release branches
            for (branch in releaseBranches) {
                val branchTagName = "$releaseName-$branch"
                logger.lifecycle("Tagging head of branch releases/$branch as $branchTagName...")
                stdOut.reset()

                logger.info("Getting branch head commit...")
                injected
                    .exec {
                        commandLine = listOf("git", "rev-parse", "releases/$branch")
                        standardOutput = stdOut
                    }
                    .assertNormalExitValue()

                val branchHead = stdOut.use { it.toString() }.trim()
                logger.info("HEAD of releases/$branch is $branchHead")

                logger.info("Tagging commit ${branchHead.take(7)} as $branchTagName")
                stdOut.reset()
                injected
                    .exec {
                        commandLine = listOf("git", "tag", branchTagName, branchHead)
                        standardOutput = stdOut
                    }
                    .assertNormalExitValue()
            }

            logger.info("All done!")
        }
    }

    register<Delete>("cleanTestPublishArtifacts") { delete(rootProject.layout.buildDirectory.dir("maven-test")) }

    register<Delete>("clean") { delete(rootProject.layout.buildDirectory) }

    wrapper { distributionType = Wrapper.DistributionType.ALL }
}
