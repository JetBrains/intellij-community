// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jupyter.core.jupyter.preview

import org.apache.http.client.utils.URIBuilder
import org.jetbrains.ide.BuiltInServerManager
import java.net.URI

/**
 * Url for HTTP and WS Jupyter services
 */
fun getJupyterBaseUrl(scheme: String): URIBuilder =
  URIBuilder().setScheme(scheme).setHost("127.0.0.1").setPort(BuiltInServerManager.getInstance().port)

fun URIBuilder.addPathSegment(string: String): URIBuilder = setPath("${path ?: ""}/${string.trimStart('/')}")
fun URIBuilder.addParameter(name: String, value: Boolean): URIBuilder = addParameter(name, value.toString())

/**
 * In contrast with [URI.resolve], this method resolves paths like it happens in filesystems.
 *
 * I.e.
 * ```
 * URI("http://example.com/foo").resolve("bar/baz") == URI("http://example.com/bar/baz")
 *
 * URI("http://example.com/foo").addPathSegment("bar/baz") == URI("http://example.com/foo/bar/baz")
 * ```
 */
fun URI.addPathSegment(string: String): URI = URIBuilder(this).addPathSegment(string).build()