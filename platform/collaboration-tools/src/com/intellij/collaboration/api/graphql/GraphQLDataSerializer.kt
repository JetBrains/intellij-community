// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.graphql

import com.intellij.collaboration.api.jsonhttp.JsonDataSerializer
import java.io.InputStream

interface GraphQLDataSerializer : JsonDataSerializer {
  fun <T> readAndTraverseGQLResponse(body: String, pathFromData: Array<out String>, clazz: Class<T>): T?
  fun <T> readAndTraverseGQLResponse(body: InputStream, pathFromData: Array<out String>, clazz: Class<T>): T?
}
