// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.jetcache

import com.google.common.hash.Hashing
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

import java.nio.charset.StandardCharsets


interface ProjectStateHashGenerator {

    fun generateHash(project: Project): ByteArray

    class SimpleProjectStateHashGenerator : ProjectStateHashGenerator {
        override fun generateHash(project: Project): ByteArray {
            val name = project.name
            return Hashing.murmur3_32().hashString(name, StandardCharsets.UTF_8).asBytes()
        }
    }

    companion object {
        val EP_NAME = ExtensionPointName.create<ProjectStateHashGenerator>("com.intellij.projectStateHashGenerator")

        fun generateHashFor(project: Project): ByteArray {
            val hashes = EP_NAME.extensions.map { it.generateHash(project) }.toList()
            val compoundHashBytes = ByteArray(hashes.sumBy { it.size })

            var k = 0
            for (i in 0..hashes.size)
                for (j in 0..hashes[i].size)
                    compoundHashBytes[k++] = hashes[i][j]

            return Hashing.murmur3_32().hashBytes(compoundHashBytes).asBytes()
        }
    }
}
