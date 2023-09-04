// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images.sync.dotnet

import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import org.jetbrains.intellij.build.images.generateIconClasses
import org.jetbrains.intellij.build.images.isImage
import org.jetbrains.intellij.build.images.shutdownAppScheduledExecutorService
import org.jetbrains.intellij.build.images.sync.*
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.name

object DotnetIconSync {

  @JvmStatic
  fun main(args: Array<String>) = sync()

  private class SyncPath(val iconsPath: String, val devPath: String)

  private const val RIDER_ICONS_RELATIVE_PATH = "Rider/Frontend/rider/icons"

  private val syncPaths = listOf(
    SyncPath("rider", "$RIDER_ICONS_RELATIVE_PATH/resources/rider"),
    SyncPath("net", "$RIDER_ICONS_RELATIVE_PATH/resources/resharper")
  )

  private val committer by lazy(::triggeredBy)
  private val context = Context().apply {
    iconFilter = { file ->
      // need to filter designers' icons using developers' icon-robots.txt
      iconRepoDir.relativize(file)
        .let(devRepoDir::resolve)
        .let(IconRobotsDataReader::isSyncSkipped)
        .not()
    }
  }

  private val targetWaveNumber by lazy {
    val prop = "icons.sync.dotnet.wave.number"
    System.getProperty(prop) ?: error("Specify property $prop")
  }
  private val branchForMerge by lazy {
    val randomPart = UUID.randomUUID().toString().substring(1..4)
    "net$targetWaveNumber-icons-sync-$randomPart"
  }
  private val mergeRobotBuildConfiguration by lazy {
    val prop = "icons.sync.dotnet.merge.robot.build.conf"
    System.getProperty(prop) ?: error("Specify property $prop")
  }
  private val skipTriggerMerge by lazy {
    val prop = "icons.sync.skip.trigger.merge"
    System.getProperty(prop)?.toBoolean() ?: false
  }
  private val customToolPath: String? by lazy {
    val prop = "icons.sync.custom.tool.path"
    System.getProperty(prop)?.takeIf { it.isNotEmpty() }
  }
  private val customToolArgs: String? by lazy {
    val prop = "icons.sync.custom.tool.args"
    System.getProperty(prop)
  }

  private fun step(msg: String) = println("\n** $msg")

  private fun checkCaseConflicts() {
    val riderIconRoot = context.devRepoRoot.resolve("Rider").resolve("Frontend").resolve("rider")
      .resolve("icons").resolve("resources").resolve("rider")
    val ideaIconRoot = context.devRepoRoot.resolve("Rider").resolve("ultimate").resolve("community")
      .resolve("platform").resolve("icons").resolve("src")
    val ideaIconIndex = mutableMapOf<String, String>()
    for(file in ideaIconRoot.toFile().walkTopDown().filter { it.isFile }) {
      val relPath = toSystemIndependentName(file.relativeTo(ideaIconRoot.toFile()).toString())
      ideaIconIndex[relPath.lowercase()] = relPath
    }
    val errors = mutableListOf<String>()
    for(file in riderIconRoot.toFile().walkTopDown().filter { it.isFile }) {
      val relPath = toSystemIndependentName(file.relativeTo(riderIconRoot.toFile()).toString())
      if (ideaIconIndex.containsKey(relPath.lowercase()) && relPath != ideaIconIndex[relPath.lowercase()]) {
        errors.add("$file->${ideaIconIndex[relPath.lowercase()]}")
      }
    }
    if (errors.isNotEmpty()) {
      error("Found case conflicts in repository: \n\t${errors.joinToString(separator = "\n\t")}")
    }
  }

  fun sync() {
    try {
      transformIconsToIdeaFormat()
      syncPaths.forEach(this::sync)
      callCustomTool()
      generateClasses()
      RiderIconsJsonGenerator.generate(context.devRepoRoot.resolve(RIDER_ICONS_RELATIVE_PATH))
      checkCaseConflicts()
      if (stageChanges().isEmpty()) {
        println("Nothing to commit")
      }
      else if (isUnderTeamCity()) {
        createBranchForMerge()
        commitChanges()
        pushBranchForMerge()
        if (!skipTriggerMerge) {
          triggerMerge()
        }
      }
      println("Done.")
    }
    finally {
      shutdownAppScheduledExecutorService()
      cleanup(context.iconRepo)
    }
  }

  private fun transformIconsToIdeaFormat() {
    step("Transforming icons from Dotnet to Idea format..")
    syncPaths.forEach {
      val path = context.iconRepo.resolve(it.iconsPath)
      DotnetIconsTransformation.transformToIdeaFormat(path)
    }
  }

  private fun sync(path: SyncPath) {
    step("Syncing icons for ${path.devPath}..")
    context.devRepoDir = context.devRepoRoot.resolve(path.devPath)
    context.iconRepoDir = context.iconRepo.resolve(path.iconsPath)
    context.devRepoDir.toFile().walkTopDown().forEach {
      if (isImage(it.toPath())) {
        it.delete() || error("Unable to delete $it")
      }
    }
    context.iconRepoDir.toFile().walkTopDown().forEach {
      if (isImage(it.toPath()) && context.iconFilter(it.toPath())) {
        val target = context.devRepoDir.resolve(context.iconRepoDir.relativize(it.toPath()))
        it.copyTo(target.toFile(), overwrite = true)
      }
    }
  }

  private fun callCustomTool() {
    customToolPath?.let {
      step("Call custom tool: $it")
      val output = execute(
        context.devRepoDir, it,
        *customToolArgs?.splitWithSpace()?.toTypedArray() ?: error("Custom tool args should be specified")
      )
      println("Custom Tool Output:")
      println(output)
    }
  }

  private fun findProjectHomePath(devRepoDir: Path): String {
    var currentPath = devRepoDir
    while (currentPath.parent != null) {
      if (currentPath.resolve("Frontend").resolve(".idea").exists()) {
        return currentPath.resolve("Frontend").toAbsolutePath().toString()
      }
      currentPath = currentPath.parent
    }
    error("can't find project home path for devRepoDir: $devRepoDir")
  }

  private fun generateClasses() {
    step("Generating classes..")
    generateIconClasses(config = DotnetIconClasses(findProjectHomePath(context.devRepoDir)))
  }

  private fun stageChanges(): Collection<String> {
    step("Staging changes..")
    val changes = gitStatus(context.devRepoRoot, includeUntracked = true).all().filter {
      val file = context.devRepoRoot.resolve(it)
      isImage(file) || file.toString().endsWith(".java") || file.name == RiderIconsJsonGenerator.GENERATED_FILE_NAME
    }
    if (changes.isNotEmpty()) {
      stageFiles(changes, context.devRepoRoot)
    }
    return changes
  }

  private fun commitChanges() {
    step("Committing changes..")
    commit(context.devRepoRoot, "Synced from ${getOriginUrl(context.iconRepo)}", committer.name, committer.email)
    val commit = commitInfo(context.devRepoRoot) ?: error("Unable to perform commit")
    println("Committed ${commit.hash} '${commit.subject}'")
  }

  private fun createBranchForMerge() {
    step("Creating branch $branchForMerge..")
    execute(context.devRepoRoot, GIT, "checkout", "-B", branchForMerge)
  }

  private fun pushBranchForMerge() {
    step("Pushing $branchForMerge..")
    push(context.devRepoRoot, branchForMerge)
  }

  private fun triggerMerge() {
    step("Triggering merge with $mergeRobotBuildConfiguration..")
    val response = triggerBuild(mergeRobotBuildConfiguration, branchForMerge)
    println("Response is $response")
  }
}