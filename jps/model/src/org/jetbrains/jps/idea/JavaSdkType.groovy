package org.jetbrains.jps.idea

import org.jetbrains.jps.JavaSdkImpl
import org.jetbrains.jps.Project
import org.jetbrains.jps.Sdk

/**
 * @author nik
 */
class JavaSdkType extends SdkTypeService {
  JavaSdkType() {
    super("JavaSDK")
  }

  @Override
  Sdk createSdk(Project project, String name, String version, String homePath, Node additionalData) {
    return new JavaSdkImpl(project, name, version, homePath, {})
  }
}
