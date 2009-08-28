package com.intellij.openapi.projectRoots.ex;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ComparatorUtil;
import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.containers.ContainerUtil.skipNulls;
import com.intellij.util.containers.Convertor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 14, 2004
 */
public class PathUtilEx {

  private static final Function<Module, Sdk> MODULE_JDK = new Function<Module, Sdk>() {
    public Sdk fun(Module module) {
      return ModuleRootManager.getInstance(module).getSdk();
    }
  };
  private static final Convertor<Sdk, String> JDK_VERSION = new Convertor<Sdk, String>() {
    public String convert(Sdk jdk) {
      return jdk.getVersionString();
    }
  };

  public static Sdk getAnyJdk(Project project) {
    return chooseJdk(project, Arrays.asList(ModuleManager.getInstance(project).getModules()));
  }

  public static Sdk chooseJdk(Project project, Collection<Module> modules) {
    Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectJdk();
    if (projectJdk != null) {
      return projectJdk;
    }
    return chooseJdk(modules);
  }

  public static Sdk chooseJdk(Collection<Module> modules) {
    List<Sdk> jdks = skipNulls(map(skipNulls(modules), MODULE_JDK));
    if (jdks.isEmpty()) {
      return null;
    }
    Collections.sort(jdks, ComparatorUtil.compareBy(JDK_VERSION, String.CASE_INSENSITIVE_ORDER));
    return jdks.get(jdks.size() - 1);
  }
}
