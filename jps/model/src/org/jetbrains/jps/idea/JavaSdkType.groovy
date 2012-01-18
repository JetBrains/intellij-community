package org.jetbrains.jps.idea

import org.jetbrains.jps.Project
import org.jetbrains.jps.Sdk
import org.jetbrains.jps.JavaSdk

/**
 * @author nik
 */
class JavaSdkType extends SdkTypeService {
  JavaSdkType() {
    super("JavaSDK")
  }

  @Override
  Sdk createSdk(Project project, String name, String homePath, Node additionalData) {
    return new JavaSdk(project, name, homePath, {})
  }
}
