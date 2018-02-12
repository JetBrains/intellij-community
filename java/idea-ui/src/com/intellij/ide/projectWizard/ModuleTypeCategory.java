package com.intellij.ide.projectWizard;

import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class ModuleTypeCategory extends ProjectCategory {

  private final ModuleType myModuleType;

  public ModuleTypeCategory(ModuleType moduleType) {
    myModuleType = moduleType;
  }

  @NotNull
  @Override
  public final ModuleBuilder createModuleBuilder() {
    return myModuleType.createModuleBuilder();
  }

  public static class Java extends ModuleTypeCategory {

    public Java() {
      super(JavaModuleType.getModuleType());
    }
  }

  public static class JavaSE extends Java {
    @Override
    public int getWeight() {
      return JavaModuleBuilder.JAVA_WEIGHT;
    }
  }
}
