// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository

import java.io.File

class MavenRepoFixture(private val myMavenRepo: File) {

public fun addAnnotationsArtifact(group: String = "myGroup",
                                   artifact: String = "myArtifact",
                                   version: String)
  : String = File(myMavenRepo, "$group/$artifact/$version/$artifact-$version-annotations.zip")
  .apply {
    parentFile.mkdirs()
    writeText("Fake annotations artifact")
  }.name

public fun generateMavenMetadata(group: String, artifact: String) {
  val metadata = File(myMavenRepo, "$group/$artifact/maven-metadata.xml")
    .apply {
      parentFile.mkdirs()
    }

  val versionsList = metadata.parentFile
    .listFiles()
    .asSequence()
    .filter { it.isDirectory }
    .map { it.name }
    .toList()

  val releaseVersion = versionsList.last()

  metadata.writeText("""
    |<?xml version="1.0" encoding="UTF-8"?>
    |<metadata>
    |  <groupId>$group</groupId>
    |  <artifactId>$artifact</artifactId>
    |  <version>$releaseVersion</version>
    |  <versioning>
    |    <latest>$releaseVersion</latest>
    |    <release>$releaseVersion</release>
    |    <versions>
    |      ${versionsList.joinToString(separator = "\n") { "<version>$it</version>" } }
    |    </versions>
    |    <lastUpdated>20180809190315</lastUpdated>
    |  </versioning>
    |</metadata>
""".trimMargin())

}
  }