// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dump.paths

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.intellij.util.indexing.diagnostic.dump.paths.providers.IdePortableFilePathProvider
import java.util.Locale

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class PortableFilePath {

  abstract val presentablePath: String
    @JsonIgnore get

  @JsonTypeName("project")
  object ProjectRoot : PortableFilePath() {
    override val presentablePath: String
      get() = "<project home>"

    override fun equals(other: Any?): Boolean = other is ProjectRoot

    /**
     * Make it constant across IDE restarts.
     */
    override fun hashCode(): Int = 42
  }

  @JsonTypeName("library")
  data class LibraryRoot(
    val libraryType: LibraryType,
    val libraryName: String,
    val moduleName: String?,
    val libraryRootIndex: Int,
    val inClassFiles: Boolean
  ) : PortableFilePath() {

    enum class LibraryType {
      APPLICATION, PROJECT, MODULE;

      override fun toString(): String = name.lowercase(Locale.getDefault())
    }

    override val presentablePath: String
      get() = "<$libraryType ${if (libraryType == LibraryType.MODULE) "'$moduleName' " else ""}library '$libraryName'>/" +
              "<library " + (if (inClassFiles) "class" else "source") + " root #$libraryRootIndex>"
  }

  @JsonTypeName("jdk")
  data class JdkRoot(
    val jdkName: String,
    val jdkRootIndex: Int,
    val inClassFiles: Boolean
  ) : PortableFilePath() {
    override val presentablePath: String
      get() = "<jdk $jdkName>/" +
              "<jdk " + (if (inClassFiles) "class" else "source") + " root #$jdkRootIndex>"
  }

  @JsonTypeName("ide")
  data class IdeRoot(val ideDirectoryType: String) : PortableFilePath() {
    init {
      require(ideDirectoryType in IdePortableFilePathProvider.IDE_PATHS.keys) { ideDirectoryType }
    }

    override val presentablePath: String
      get() = "<ide $ideDirectoryType dir>"
  }

  @JsonTypeName("archive")
  data class ArchiveRoot(val archiveLocalPath: PortableFilePath) : PortableFilePath() {
    override val presentablePath: String
      get() = archiveLocalPath.presentablePath + "!/"
  }

  @JsonTypeName("absolute")
  data class AbsolutePath(val absoluteUrl: String) : PortableFilePath() {
    override val presentablePath: String
      get() = "<absolute>/$absoluteUrl"
  }

  @JsonTypeName("relative")
  data class RelativePath(val root: PortableFilePath, val relativePath: String) : PortableFilePath() {
    init {
      require(!relativePath.startsWith('/')) { relativePath }
    }

    override val presentablePath: String
      get() = root.presentablePath.trimEnd('/') + '/' + relativePath
  }

  override fun toString(): String = presentablePath
}