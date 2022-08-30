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
    LibraryLicense(name = "Eclipse LSP4J", libraryName = "org.eclipse.lsp4j:org.eclipse.lsp4j:0.12.0", license = "Eclipse Public License 1.0"),
    LibraryLicense(name = "Eclipse LSP4J", libraryName = "org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.12.0", license = "Eclipse Public License 1.0"),
    LibraryLicense(name = "JTS IO Common", libraryName = "jts-io-common", license = "Eclipse Public License 2.0"),
    LibraryLicense(name = "Xtext", libraryName = "xtext-xbase", license = "Eclipse Public License 1.0"),
  )
}
