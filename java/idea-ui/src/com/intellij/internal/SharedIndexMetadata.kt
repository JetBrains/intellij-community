// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.indexing.IndexInfrastructureVersion

object SharedIndexMetadata {
  fun writeIndexMetadata(indexName: String,
                         indexKind: String,
                         sourcesHash: String,
                         infrastructureVersion: IndexInfrastructureVersion): ByteArray {
    try {
      val om = ObjectMapper()

      val root = om.createObjectNode()

      root.put("metadata_version", "2")

      root.putObject("sources").also { sources ->
        sources.put("os", IndexInfrastructureVersion.getOs().osName)
        sources.put("hash", sourcesHash)
        sources.put("kind", indexKind)
        sources.put("name", indexName)
      }

      root.putObject("sources").also { build ->
        build.put("os", IndexInfrastructureVersion.getOs().osName)
        build.put("os_name", SystemInfo.getOsNameAndVersion())
        build.put("intellij_version", ApplicationInfo.getInstance().fullVersion)
        build.put("intellij_build", ApplicationInfo.getInstance().build.toString())
        build.put("intellij_product_code", ApplicationInfo.getInstance().build.productCode)
      }

      root.putObject("indexes").also { indexes ->
        indexes.put("weak_hash", infrastructureVersion.weakVersionHash)
        indexes.putObjectFromMap("base_versions", infrastructureVersion.baseIndexes)
        indexes.putObjectFromMap("file_index_versions", infrastructureVersion.fileBasedIndexVersions)
        indexes.putObjectFromMap("stub_index_versions", infrastructureVersion.stubIndexVersions)
      }

      return om.writerWithDefaultPrettyPrinter().writeValueAsBytes(root)
      //NOTE: should we include information about index sizes here too?
    } catch (t: Throwable) {
      throw RuntimeException("Failed to generate shared index metadata JSON. ${t.message}", t)
    }
  }

  private fun ObjectNode.putObjectFromMap(name: String, map: Map<String, String>) {
    putObject(name).also { obj ->
      map.toSortedMap().forEach { (k, v) -> obj.put(k, v) }
    }
  }
}
