// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.notebooks.jupyter.preview

import org.apache.http.client.utils.URIBuilder
import org.jetbrains.ide.BuiltInServerManager

/**
 * Url for HTTP and WS Jupyter services
 */
fun getJupyterBaseUrl(scheme: String): URIBuilder =
  URIBuilder().setScheme(scheme).setHost("127.0.0.1").setPort(BuiltInServerManager.getInstance().port)

fun URIBuilder.addPathSegment(string: String): URIBuilder = setPath((path ?: "") + "/$string")
fun URIBuilder.addParameter(name: String, value: Boolean): URIBuilder = addParameter(name, value.toString())
