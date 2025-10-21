/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build

object AndroidStudioLibraryLicenses {
  @JvmStatic
  val LICENSES_LIST: List<LibraryLicense> = listOf(
    LibraryLicense(name = "Eclipse LSP4J", libraryName = "eclipse.lsp4j", url = "https://github.com/eclipse/lsp4j").eplV2("https://github.com/eclipse-lsp4j/lsp4j/blob/main/LICENSE"),
    LibraryLicense(name = "Eclipse LSP4J Debug", libraryName = "eclipse.lsp4j.debug", url = "https://github.com/eclipse/lsp4j").eplV2("https://github.com/eclipse-lsp4j/lsp4j/blob/main/LICENSE"),
    LibraryLicense(name = "Eclipse LSP4J JSON RPC", libraryName = "eclipse.lsp4j.jsonrpc", url = "https://github.com/eclipse/lsp4j").eplV2("https://github.com/eclipse-lsp4j/lsp4j/blob/main/LICENSE"),
    LibraryLicense(name = "Eclipse LSP4J JSON RPC Debug", libraryName = "eclipse.lsp4j.jsonrpc.debug", url = "https://github.com/eclipse/lsp4j").eplV2("https://github.com/eclipse-lsp4j/lsp4j/blob/main/LICENSE"),
    LibraryLicense(name = "JTS IO Common", libraryName = "jts-io-common", url = "https://github.com/locationtech/jts").eplV2("https://github.com/locationtech/jts/blob/master/LICENSE_EPLv2.txt"),
    LibraryLicense(name = "Mockito", libraryName = "mockito", url = "https://github.com/mockito/mockito").mit("https://github.com/mockito/mockito/blob/main/LICENSE"),
    LibraryLicense(name = "Kryo", libraryName = "Kryo", url = "https://github.com/EsotericSoftware/kryo")
      .newBsd("https://github.com/EsotericSoftware/kryo/blob/master/LICENSE.md"),
  )
}
