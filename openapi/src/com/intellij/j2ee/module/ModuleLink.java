package com.intellij.j2ee.module;

import com.intellij.openapi.module.Module;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


/**
 * @author Alexey Kudravtsev
 */
public abstract class ModuleLink extends ContainerElement {
  protected static Map<J2EEPackagingMethod, String> methodToDescription = new HashMap<J2EEPackagingMethod, String>();

  public ModuleLink(Module parentModule) {
    super(parentModule);
  }

  public abstract Module getModule();

  public abstract String getId();

  public abstract String getName();

  public static String getId(Module module) {
    return module == null ? "" : new File(module.getModuleFilePath()).getName();
  }
}
