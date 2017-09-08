/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import groovy.transform.Canonical
import groovy.transform.CompileStatic
/**
 * Describes proprietary tools which are used to build the product. Pass the instance of this class {@link BuildContext#createContext} method.
 *
 * @author nik
 */
@CompileStatic
@Canonical
class ProprietaryBuildTools {
  public static final ProprietaryBuildTools DUMMY = new ProprietaryBuildTools(null, null, null, null)

  /**
   * This tool is required to sign *.exe files in Windows distribution. If it is {@code null} the files won't be signed and Windows may show
   * a warning when user tries to run them.
   */
  SignTool signTool

  /**
   * This tool is used to scramble the main product JAR file if {@link ProductProperties#scrambleMainJar} is {@code true}
   */
  ScrambleTool scrambleTool

  /**
   * Describes address and credentials of Mac machine which is used to sign and build *.dmg installer for macOS. If {@code null} only *.sit
   * archive will be built.
   */
  MacHostProperties macHostProperties

  /**
   * Describes a server that can be used to download built artifacts
   */
  ArtifactsServer artifactsServer
}