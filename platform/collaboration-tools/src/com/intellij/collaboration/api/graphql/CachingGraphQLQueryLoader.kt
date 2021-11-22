// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.api.graphql

import com.github.benmanes.caffeine.cache.Caffeine
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.time.temporal.ChronoUnit

abstract class CachingGraphQLQueryLoader(private val fragmentsDirectory: String = "graphql/fragment",
                                         private val fragmentsFileExtension: String = "graphql") {

  private val fragmentDefinitionRegex = Regex("fragment (.*) on .*\\{")

  private val fragmentsCache = Caffeine.newBuilder()
    .expireAfterAccess(Duration.of(2, ChronoUnit.MINUTES))
    .build<String, Fragment>()

  private val queriesCache = Caffeine.newBuilder()
    .expireAfterAccess(Duration.of(1, ChronoUnit.MINUTES))
    .build<String, String>()

  // Use path to allow going to the file quickly
  @Throws(IOException::class)
  fun loadQuery(queryPath: String): String {
    return queriesCache.get(queryPath) { path ->
      val (body, fragmentNames) = readCollectingFragmentNames(path)

      val builder = StringBuilder()
      val fragments = LinkedHashMap<String, Fragment>()
      readFragmentsWithDependencies(fragmentNames, fragments)
      for (fragment in fragments.values.reversed()) {
        builder.append(fragment.body).append("\n")
      }

      builder.append(body)
      builder.toString()
    }
  }

  private fun readFragmentsWithDependencies(names: Set<String>, into: MutableMap<String, Fragment>) {
    for (fragmentName in names) {
      val fragment = fragmentsCache.get(fragmentName) { name ->
        Fragment(name)
      }
      into[fragment.name] = fragment

      val nonProcessedDependencies = fragment.dependencies.filter { !into.contains(it) }.toSet()
      readFragmentsWithDependencies(nonProcessedDependencies, into)
    }
  }

  private fun readCollectingFragmentNames(filePath: String): Pair<String, Set<String>> {
    val bodyBuilder = StringBuilder()
    val fragments = mutableSetOf<String>()
    val innerFragments = mutableSetOf<String>()

    val stream = getFileStream(filePath)
                 ?: throw GraphQLFileNotFoundException("Couldn't find file $filePath")
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
    return bodyBuilder.toString().removeSuffix("\n") to fragments
  }

  // visible to avoid storing test queries with the code
  @VisibleForTesting
  protected open fun getFileStream(relativePath: String): InputStream? = this::class.java.classLoader.getResourceAsStream(relativePath)

  private inner class Fragment(val name: String) {

    val body: String
    val dependencies: Set<String>

    init {
      val (body, dependencies) = readCollectingFragmentNames("$fragmentsDirectory/${name}.$fragmentsFileExtension")
      this.body = body
      this.dependencies = dependencies
    }


    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Fragment) return false

      if (name != other.name) return false

      return true
    }

    override fun hashCode(): Int {
      return name.hashCode()
    }
  }
}