package com.intellij.testFramework.fixtures;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ModifiableRootModel;

/**
* @author peter
*/
public class DefaultLightProjectDescriptor implements LightProjectDescriptor {
  public ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  public Sdk getSdk() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }

  public void configureModule(Module module, ModifiableRootModel model) {
  }
}
