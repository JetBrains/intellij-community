// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.api.graphql

import com.github.benmanes.caffeine.cache.Caffeine
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentMap

/**
 * Built on the assumption that fragments are named the same as the file they're located in.
 * Ex:
 *
 * /graphql/fragment/actor.graphql
 * only publicly exposes the `actor` fragment
 */
@ApiStatus.Internal
class CachingGraphQLQueryLoader(
  private val getFileStream: (relativePath: String) -> InputStream?,
  private val fragmentsCache: ConcurrentMap<String, Block> = createFragmentCache(),
  private val queriesCache: ConcurrentMap<String, String> = createQueryCache(),
  private val fragmentsDirectories: List<String> = listOf("graphql/fragment"),
  private val fragmentsFileExtension: String = "graphql"
) : GraphQLQueryLoader {

  private val fragmentDefinitionRegex = Regex("fragment (.*) on .*\\{")

  @Throws(IOException::class)
  override fun loadQuery(queryPath: String): String = getFileStream(queryPath)?.use {
    loadQuery(it, queryPath)
  } ?: throw GraphQLFileNotFoundException("Couldn't find query file at $queryPath")

  @Throws(IOException::class)
  private fun loadQuery(stream: InputStream, key: String): String {
    return queriesCache.computeIfAbsent(key) {
      loadQuery(stream)
    }
  }

  /**
   * Doesn't cache the resulting query.
   */
  @Throws(IOException::class)
  fun loadQuery(stream: InputStream): String {
    val (body, fragmentNames) = readBlock(stream)

    val builder = StringBuilder()
    val fragments = LinkedHashMap<String, Block>()
    readFragmentsInto(fragmentNames, fragments)
    for (fragment in fragments.values.reversed()) {
      builder.append(fragment.body).append("\n")
    }

    builder.append(body)
    return builder.toString()
  }

  private fun readFragmentsInto(names: Set<String>, into: MutableMap<String, Block>) {
    for (fragmentName in names) {
      val fragment = fragmentsDirectories.firstNotNullOfOrNull {
        val path = "$it/${fragmentName}.$fragmentsFileExtension"
        fragmentsCache.computeIfAbsent(path) {
          getFileStream(path)?.use { stream -> readBlock(stream) }
        }
      } ?: throw GraphQLFileNotFoundException("Couldn't find file for fragment $fragmentName")
      into[fragmentName] = fragment

      val nonProcessedDependencies = fragment.dependencies.filter { !into.contains(it) }.toSet()
      readFragmentsInto(nonProcessedDependencies, into)
    }
  }

  private fun readBlock(stream: InputStream): Block {
    val bodyBuilder = StringBuilder()
    val fragments = mutableSetOf<String>()
    val innerFragments = mutableSetOf<String>()

    stream.reader().forEachLine {
      val line = it.trim()
      bodyBuilder.append(line).append("\n")

      if (line.startsWith("fragment")) {
        val fragmentName = fragmentDefinitionRegex.matchEntire(line)?.groupValues?.get(1)?.trim()
        if (fragmentName != null)
          innerFragments.add(fragmentName)
      }

      if (line.startsWith("...") && line.length > 3 && !line[3].isWhitespace()) {
        val fragmentName = line.substring(3)
        fragments.add(fragmentName)
      }
    }
    fragments.removeAll(innerFragments)
    return Block(bodyBuilder.toString().removeSuffix("\n"), fragments)
  }

  companion object {
    /**
     * @param body The body of the query to be sent to the server.
     * @param dependencies The set of fragments that the query depends on listed by name.
     */
    data class Block(val body: String, val dependencies: Set<String>)

    fun createFragmentCache(): ConcurrentMap<String, Block> =
      Caffeine.newBuilder()
        .expireAfterAccess(Duration.of(2, ChronoUnit.MINUTES))
        .build<String, Block>()
        .asMap()

    fun createQueryCache(): ConcurrentMap<String, String> =
      Caffeine.newBuilder()
        .expireAfterAccess(Duration.of(1, ChronoUnit.MINUTES))
        .build<String, String>()
        .asMap()
  }
}