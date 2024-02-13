import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.composeDesktop) apply false
    `jewel-linting`
}

dependencies {
    sarif(projects.decoratedWindow)
    sarif(projects.foundation)
    sarif(projects.ideLafBridge)
    sarif(projects.intUi.intUiDecoratedWindow)
    sarif(projects.intUi.intUiStandalone)
    sarif(projects.markdown.core)
    sarif(projects.markdown.extensionGfmAlerts)
    sarif(projects.samples.idePlugin)
    sarif(projects.samples.standalone)
    sarif(projects.ui)
}

tasks {
    val mergeSarifReports by registering(MergeSarifTask::class) {
        source(configurations.outgoingSarif)
        include { it.file.extension == "sarif" }
    }

    register("check") { dependsOn(mergeSarifReports) }

    register("tagRelease") {
        dependsOn("check")

        description = "Tags main branch and releases branches with provided tag name"
        group = "release"

        doFirst {
            val tagName = (project.property("tagName") as String?)
                ?.takeIf { it.isNotBlank() }
                ?: throw GradleException("Please provide a tag name using -PtagName=[value]")

            val stdOut = ByteArrayOutputStream()

            // Check we're on the main branch
            logger.info("Checking current branch is main...")
            exec {
                commandLine = listOf("git", "rev-parse", "--abbrev-ref", "HEAD")
                standardOutput = stdOut
            }.assertNormalExitValue()

            val currentBranch = stdOut.use { it.toString() }.trim()
            if (currentBranch != "main") {
                throw GradleException("This task must only be run on the main branch")
            }

            // Check tag doesn't already exist
            logger.info("Checking current branch is main...")
            exec {
                commandLine = listOf("git", "tag")
                standardOutput = stdOut
            }.assertNormalExitValue()

            if (stdOut.toString().trim().lines().any { it == tagName }) {
                throw GradleException("The tag $tagName already exists!")
            }

            // Get the current HEAD hash
            logger.info("Getting HEAD hash...")
            stdOut.reset()
            exec {
                commandLine = listOf("git", "rev-parse", "HEAD")
                standardOutput = stdOut
            }.assertNormalExitValue()

            val currentHead = stdOut.use { it.toString() }.trim()

            // Enumerate the release branches
            logger.info("Enumerating release branches...")
            stdOut.reset()
            exec {
                commandLine = listOf("git", "branch")
                standardOutput = stdOut
            }.assertNormalExitValue()

            val releaseBranches = stdOut.use { it.toString() }
                .lines()
                .filter { it.trim().startsWith("releases/") }
                .map { it.trim().removePrefix("releases/") }

            logger.lifecycle("Release branches: ${releaseBranches.joinToString { "releases/$it" }}")

            // Check all release branches have gotten the latest from main
            logger.info("Validating release branches...")
            for (branch in releaseBranches) {
                stdOut.reset()
                exec {
                    commandLine = listOf("git", "merge-base", "main", "releases/$branch")
                    standardOutput = stdOut
                }.assertNormalExitValue()

                val mergeBase = stdOut.use { it.toString() }.trim()
                if (mergeBase != currentHead) {
                    throw GradleException("Branch releases/$branch is not up-to-date with main!")
                }
            }

            // Tag main branch
            logger.lifecycle("Tagging head of main branch as $tagName...")
            exec {
                commandLine = listOf("git", "tag", tagName)
            }.assertNormalExitValue()

            // Tag release branches
            for (branch in releaseBranches) {
                val branchTagName = "$tagName-$branch"
                logger.lifecycle("Tagging head of branch releases/$branch as $branchTagName...")
                stdOut.reset()

                logger.info("Getting branch head commit...")
                exec {
                    commandLine = listOf("git", "rev-parse", "releases/$branch")
                    standardOutput = stdOut
                }.assertNormalExitValue()

                val branchHead = stdOut.use { it.toString() }.trim()
                logger.info("HEAD of releases/$branch is $branchHead")

                logger.info("Tagging commit ${branchHead.take(7)} as $branchTagName")
                stdOut.reset()
                exec {
                    commandLine = listOf("git", "tag", branchTagName, branchHead)
                    standardOutput = stdOut
                }.assertNormalExitValue()
            }

            logger.info("All done!")
        }
    }
}
