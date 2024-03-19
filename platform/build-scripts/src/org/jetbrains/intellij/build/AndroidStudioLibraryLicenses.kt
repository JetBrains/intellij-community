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
    LibraryLicense(name = "Eclipse LSP4J", libraryName = "eclipse.lsp4j", url = "https://github.com/eclipse/lsp4j").eplV2(),
    LibraryLicense(name = "Eclipse LSP4J JSON RPC", libraryName = "eclipse.lsp4j.jsonrpc", url = "https://github.com/eclipse/lsp4j").eplV2(),
    LibraryLicense(name = "JTS IO Common", libraryName = "jts-io-common", url = "https://github.com/locationtech/jts").eplV2(),
    LibraryLicense(name = "Xtext", libraryName = "xtext-xbase", url = "https://eclipse.dev/Xtext").eplV1(),
    LibraryLicense(name = "Kryo", libraryName = "Kryo", url = "https://github.com/EsotericSoftware/kryo")
      .newBsd("https://github.com/EsotericSoftware/kryo/blob/master/LICENSE.md"),
    // TODO(b/330399456): error-prone-annotations is used by ASwB only; this lib should be moved outside the platform.
    LibraryLicense(name = "error-prone-annotations", libraryName = "error-prone-annotations", url = "https://github.com/google/error-prone")
      .apache("https://github.com/google/error-prone/blob/master/COPYING")
      .suppliedByOrganizations(SoftwareBillOfMaterials.Companion.Suppliers.GOOGLE),
  )
}
