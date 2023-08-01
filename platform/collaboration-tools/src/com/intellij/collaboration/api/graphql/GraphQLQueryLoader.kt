// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.graphql

import java.io.IOException

interface GraphQLQueryLoader {
  @Throws(IOException::class)
  fun loadQuery(queryPath: String): String
}