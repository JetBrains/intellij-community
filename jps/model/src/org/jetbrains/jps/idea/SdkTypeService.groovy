package org.jetbrains.jps.idea

import org.jetbrains.jps.Project
import org.jetbrains.jps.Sdk

/**
 * @author nik
 */
public abstract class SdkTypeService {
  final String typeName

  SdkTypeService(String typeName) {
    this.typeName = typeName
  }

  public abstract Sdk createSdk(Project project, String name, String version, String homePath, Node additionalData)
}
