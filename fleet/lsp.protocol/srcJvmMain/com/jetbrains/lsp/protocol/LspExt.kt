// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lsp.protocol

fun URI.asJavaUri(): java.net.URI = java.net.URI(uri)