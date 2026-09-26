// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images.sync

import org.jetbrains.intellij.build.images.generateIconClasses
import org.jetbrains.intellij.build.images.isImage
import org.jetbrains.intellij.build.images.shutdownAppScheduledExecutorService
import kotlin.io.path.extension

fun main(args: Array<String>) = try {
  require(!isUnderTeamCity())
  if (args.isEmpty()) System.err.println("If you haven't intended to start full icons sync" +
                                         " then please specify required icons repo's commit hashes" +
                                         " joined by comma, semicolon or space in arguments")
  System.setProperty(Context.iconsCommitHashesToSyncArg, args.joinToString())
  val context = Context()
  val project = context.devRepoDir
  val existingChanges = gitStatus(project, includeUntracked = true).all().toSet()
  if (existingChanges.isNotEmpty()) {
    System.err.println("Following exiting changes are going to be ignored:")
    existingChanges.forEach(System.err::println)
  }
  echo("Syncing icons..")
  checkIcons(context)
  echo("Generating classes..")
  generateIconClasses()
  val changes = gitStatus(project, includeUntracked = true).all().asSequence().filter {
    val file = project.resolve(it)
    isImage(file) || file.extension == "java" || file.extension == "kt"
  }.minus(existingChanges).toList()
  if (changes.isNotEmpty()) {
    echo("Staging files:")
    changes.forEach(::echo)
    stageFiles(changes, project)
    commit(project, context.iconsCommitsToSync.commitMessage())
    val newCommit = commitInfo(project)
    requireNotNull(newCommit)
    echo("Done, ${newCommit.hash} commit was created locally. Please safe-push it, see plugins/safe-push/README.md for details.")
  }
  else {
    echo("Nothing to sync")
  }
}
finally {
  shutdownAppScheduledExecutorService()
}

private fun echo(msg: String) = println("\n** $msg")