// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync.dotnet

import org.jetbrains.intellij.build.images.generateIconsClasses
import org.jetbrains.intellij.build.images.sync.Context
import org.jetbrains.intellij.build.images.sync.checkIcons
import java.nio.file.Paths

fun main() {
  val context = Context()
  echo("Transforming icons from Dotnet to Idea format..")
  DotnetIconsTransformation.transformToIdeaFormat(Paths.get("/Users/jetbrains/IdeaProjects/IntelliJIcons/net"))
  echo("Generating classes..")
  generateIconsClasses("/Users/jetbrains/IdeaProjects/dotnet-products/Rider") {
    it.name == "rider-icons"
  }
  echo("Syncing icons..")
  checkIcons(context)
}

private fun echo(msg: String) = println("\n** $msg")