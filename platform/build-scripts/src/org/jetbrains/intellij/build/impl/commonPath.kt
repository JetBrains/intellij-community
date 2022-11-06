// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

// https://rosettacode.org/wiki/Find_common_directory_path#Java
fun getCommonPath(paths: List<String>): String {
  var commonPath = ""
  val folders = Array(paths.size) { paths[it].split('/') }
  for (j in folders[0].indices) {
    // grab the next folder name in the first path
    val thisFolder = folders[0][j]
    // assume all have matched in case there are no more paths
    var allMatched = true
    var i = 1
    while (i < folders.size && allMatched) {
      // look at the other paths
      if (folders[i].size < j) { // if there is no folder here
        allMatched = false // no match
        break // stop looking because we've gone as far as we can
      }
      //otherwise
      allMatched = folders[i][j] == thisFolder //check if it matched
      i++
    }

    if (!allMatched) {
      break
    }

    commonPath += "$thisFolder/"
  }
  return commonPath
}