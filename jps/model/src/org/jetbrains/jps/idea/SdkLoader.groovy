package org.jetbrains.jps.idea

import org.jetbrains.jps.Project
import org.jetbrains.jps.Sdk

/**
 * @author nik
 */
public class SdkLoader {
  private static final OwnServiceLoader<SdkTypeService> sdkTypeLoader = OwnServiceLoader.load(SdkTypeService.class)
  private static Map<String, SdkTypeService> sdkTypes

  public static Sdk createSdk(Project project, String typeName, String sdkName, String version, String homePath, Node additionalData) {
    def type = findSdkType(typeName)
    if (type == null) {
      return null
    }
    return type.createSdk(project, sdkName, version, homePath, additionalData)
  }

  private static SdkTypeService findSdkType(String typeName) {
    if (sdkTypes == null) {
      sdkTypes = [:]
      sdkTypeLoader.each {SdkTypeService sdkType ->
        sdkTypes[sdkType.typeName] = sdkType
      }
    }
    return sdkTypes[typeName]
  }
}
