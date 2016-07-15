/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build

/**
 * @author nik
 */
class LibraryLicense {
  private static final Map<String, String> PREDEFINED_LICENSE_URLS = ["Apache 2.0": "http://www.apache.org/licenses/LICENSE-2.0"]
  public static final String JETBRAINS_OWN = "JetBrains"

  String name, url, version
  List<String> libraryNames
  String license, licenseUrl
  String attachedTo

  public static LibraryLicense libraryLicense(Map args) {
    if (args.libraryNames == null) {
      args.libraryNames = [args.libraryName ?: args.name]
      args.remove("libraryName")
    }
    if (args.licenseUrl == null) {
      args.licenseUrl = PREDEFINED_LICENSE_URLS[args.license]
    }
    new LibraryLicense(args)
  }

  public static jetbrainsLibrary(String libraryName) {
    libraryLicense(name: libraryName, license: JETBRAINS_OWN)
  }
}