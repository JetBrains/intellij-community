package org.jetbrains.jps;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.Set;

/**
 * @author max
 */
public class ModuleChunk {
  private static final NotNullFunction<JpsModule,String> GET_NAME = new NotNullFunction<JpsModule, String>() {
    @NotNull
    @Override
    public String fun(JpsModule dom) {
      return dom.getName();
    }
  };
  private Set<JpsModule> myModules;

  public ModuleChunk(Set<JpsModule> modules) {
    myModules = modules;
  }

  public String getName() {
    if (myModules.size() == 1) return myModules.iterator().next().getName();
    return "ModuleChunk(" + StringUtil.join(myModules, GET_NAME, ",") + ")";
  }

  public Set<JpsModule> getModules() {
    return myModules;
  }

  public String toString() {
    return getName();
  }

  public JpsProject getProject() {
    return representativeModule().getProject();
  }

  public JpsModule representativeModule() {
    return myModules.iterator().next();
  }
}
