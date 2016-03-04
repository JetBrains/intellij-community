/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import groovy.transform.Immutable

/**
 * @author nik
 */
class ApplicationInfoProperties {
  final String majorVersion
  final String minorVersion
  final boolean isEAP
  final InstallOverProperties installOver

  ApplicationInfoProperties(String appInfoXmlPath) {
    def root = new XmlParser().parse(new File(appInfoXmlPath))
    majorVersion = root.version.first().@major
    minorVersion = root.version.first().@minor
    isEAP = Boolean.parseBoolean(root.version.first().@eap)
    def installOverTag = root."install-over".first()
    installOver = new InstallOverProperties(minBuild: installOverTag.@minbuild, maxBuild: installOverTag.@maxbuild, version: installOverTag.@version)
  }
}

@Immutable
class InstallOverProperties {
  final String minBuild
  final String maxBuild
  final String version
}