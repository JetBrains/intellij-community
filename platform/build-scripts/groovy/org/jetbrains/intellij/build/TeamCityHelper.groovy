// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.util.containers.HashMap
import groovy.transform.CompileStatic
import org.jetbrains.annotations.Nullable

@CompileStatic
@SuppressWarnings("GroovyUnusedDeclaration")
class TeamCityHelper {
  @Lazy @Nullable static File checkoutDirectory = {
    if (!isUnderTeamCity) {
      return null
    }

    def name = "teamcity.build.checkoutDir"

    def value = systemProperties[name]
    if (value == null || value.isEmpty()) {
      throw new RuntimeException("TeamCity system property $name was not found while running under TeamCity")
    }

    def file = new File(value)
    if (!file.exists()) {
      throw new RuntimeException("TeamCity system property $name contains non existent directory: $file")
    }

    return file
  }()

  @Lazy static Map<String, String> systemProperties = {
    if (!isUnderTeamCity) {
      return new HashMap<String, String>()
    }

    def systemPropertiesEnvName = "TEAMCITY_BUILD_PROPERTIES_FILE"

    def systemPropertiesFile = System.getenv(systemPropertiesEnvName)
    if (systemPropertiesFile == null || systemPropertiesFile.isEmpty()) {
      throw new RuntimeException("TeamCity environment variable $systemPropertiesEnvName was not found while running under TeamCity")
    }

    def file = new File(systemPropertiesFile)
    if (!file.exists()) {
      throw new RuntimeException("TeamCity system properties file is not found: " + file)
    }

    return loadPropertiesFile(file)
  }()

  @Lazy static Map<String, String> allProperties = {
    if (!isUnderTeamCity) {
      return new HashMap<String, String>()
    }

    def propertyName = "teamcity.configuration.properties.file"

    def value = systemProperties[propertyName]
    if (value == null || value.isEmpty()) {
      throw new RuntimeException("TeamCity system property '$propertyName' is not found")
    }

    def file = new File(value)
    if (!file.exists()) {
      throw new RuntimeException("TeamCity configuration properties file was not found: $file")
    }

    return loadPropertiesFile(file)
  }()

  @Lazy static boolean isUnderTeamCity = {
    def version = System.getenv("TEAMCITY_VERSION")
    if (version != null) {
      System.out.println("TeamCityHelper: running under TeamCity $version")
    } else {
      System.out.println("TeamCityHelper: NOT running under TeamCity")
    }

    return version != null
  }()

  private static Map<String, String> loadPropertiesFile(File file) {
    def result = new HashMap<String, String>()

    def properties = new Properties()
    file.withReader { properties.load(it) }
    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      result.put((String) entry.key, (String) entry.value)
    }

    return result
  }
}
