package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.PathUtil;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.incremental.fs.RootDescriptor;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/11/12
 */
public class ModuleRootsIndex {
  private final Map<File, RootDescriptor> myRootToDescriptorMap = new HashMap<File, RootDescriptor>();
  private final Map<Module, List<RootDescriptor>> myModuleToRootsMap = new HashMap<Module, List<RootDescriptor>>();
  private final Map<String, Module> myNameToModuleMap = new HashMap<String, Module>();
  private final int myTotalModuleCount;
  private final Set<File> myExcludedRoots = new HashSet<File>();

  private static final Key<Map<File, RootDescriptor>> ROOT_DESCRIPTOR_MAP = Key.create("_root_to_descriptor_map");
  private static final Key<Map<Module, List<RootDescriptor>>> MODULE_ROOT_MAP = Key.create("_module_to_root_map");

  public ModuleRootsIndex(Project project) {
    final Collection<Module> allModules = project.getModules().values();
    myTotalModuleCount = allModules.size();
    for (final Module module : allModules) {
      final String moduleName = module.getName();

      myNameToModuleMap.put(moduleName, module);

      List<RootDescriptor> moduleRoots = myModuleToRootsMap.get(module);
      if (moduleRoots == null) {
        moduleRoots = new ArrayList<RootDescriptor>();
        myModuleToRootsMap.put(module, moduleRoots);
      }
      Set<String> generatedRoots = module.getGeneratedSourceRoots();
      if (generatedRoots == null) {
        generatedRoots = Collections.emptySet();
      }
      for (String r : module.getSourceRoots()) {
        final File root = new File(FileUtil.toCanonicalPath(r));
        final RootDescriptor descriptor = new RootDescriptor(moduleName, root, false, generatedRoots.contains(r), false);
        myRootToDescriptorMap.put(root, descriptor);
        moduleRoots.add(descriptor);
      }
      for (String r : module.getTestRoots()) {
        final File root = new File(FileUtil.toCanonicalPath(r));
        final RootDescriptor descriptor = new RootDescriptor(moduleName, root, true, generatedRoots.contains(r), false);
        myRootToDescriptorMap.put(root, descriptor);
        moduleRoots.add(descriptor);
      }
      for (String r : module.getOwnExcludes()) {
        final File root = new File(FileUtil.toCanonicalPath(r));
        myExcludedRoots.add(root);
      }
    }
  }

  public int getTotalModuleCount() {
    return myTotalModuleCount;
  }

  @Nullable
  public Module getModuleByName(String module) {
    return myNameToModuleMap.get(module);
  }

  @NotNull
  public List<RootDescriptor> getModuleRoots(@Nullable CompileContext context, String moduleName) {
    final Module module = getModuleByName(moduleName);
    if (module == null) {
      return Collections.emptyList();
    }
    return getModuleRoots(context, module);
  }

  @NotNull
  public List<RootDescriptor> getModuleRoots(@Nullable CompileContext context, Module module) {
    List<RootDescriptor> descriptors = myModuleToRootsMap.get(module);
    if (context != null) {
      final Map<Module, List<RootDescriptor>> contextMap = MODULE_ROOT_MAP.get(context);
      if (contextMap != null) {
        final List<RootDescriptor> tempDescriptors = contextMap.get(module);
        if (tempDescriptors != null) {
          if (descriptors != null) {
            descriptors = new ArrayList<RootDescriptor>(descriptors);
            descriptors.addAll(tempDescriptors);
          }
          else {
            descriptors = tempDescriptors;
          }
        }
      }
    }
    return descriptors != null? Collections.unmodifiableList(descriptors) : Collections.<RootDescriptor>emptyList();
  }

  @Nullable
  public RootDescriptor getRootDescriptor(@Nullable CompileContext context, File root) {
    final RootDescriptor descriptor = myRootToDescriptorMap.get(root);
    if (descriptor != null) {
      return descriptor;
    }
    if (context != null) {
      final Map<File, RootDescriptor> contextMap = ROOT_DESCRIPTOR_MAP.get(context);
      if (contextMap != null) {
        return contextMap.get(root);
      }
    }
    return null;
  }

  @NotNull
  public RootDescriptor associateRoot(@NotNull CompileContext context, File root, Module module, boolean isTestRoot, final boolean isForGeneratedSources, final boolean isTemp) {
    Map<File, RootDescriptor> rootToDescriptorMap;
    Map<Module, List<RootDescriptor>> moduleToRootMap;
    if (isTemp) {
      rootToDescriptorMap = ROOT_DESCRIPTOR_MAP.get(context);
      if (rootToDescriptorMap == null) {
        rootToDescriptorMap = new HashMap<File, RootDescriptor>();
        ROOT_DESCRIPTOR_MAP.set(context, rootToDescriptorMap);
      }

      moduleToRootMap = MODULE_ROOT_MAP.get(context);
      if (moduleToRootMap == null) {
        moduleToRootMap = new HashMap<Module, List<RootDescriptor>>();
        MODULE_ROOT_MAP.set(context, moduleToRootMap);
      }
    }
    else {
      rootToDescriptorMap = myRootToDescriptorMap;
      moduleToRootMap = myModuleToRootsMap;
    }

    final RootDescriptor d = rootToDescriptorMap.get(root);
    if (d != null) {
      return d;
    }

    List<RootDescriptor> moduleRoots = moduleToRootMap.get(module);
    if (moduleRoots == null) {
      moduleRoots = new ArrayList<RootDescriptor>();
      moduleToRootMap.put(module, moduleRoots);
    }
    final RootDescriptor descriptor = new RootDescriptor(module.getName(), root, isTestRoot, isForGeneratedSources, isTemp);
    rootToDescriptorMap.put(root, descriptor);
    moduleRoots.add(descriptor);
    return descriptor;
  }

  @NotNull
  public Collection<RootDescriptor> clearTempRoots(@NotNull CompileContext context) {
    try {
      final Map<File, RootDescriptor> map = ROOT_DESCRIPTOR_MAP.get(context);
      return map != null? map.values() : Collections.<RootDescriptor>emptyList();
    }
    finally {
      MODULE_ROOT_MAP.set(context, null);
      ROOT_DESCRIPTOR_MAP.set(context, null);
    }
  }

  @Nullable
  public RootDescriptor getModuleAndRoot(@Nullable CompileContext context, File file) {
    File current = file;
    while (current != null) {
      final RootDescriptor descriptor = getRootDescriptor(context, current);
      if (descriptor != null) {
        return descriptor;
      }
      current = FileUtil.getParentFile(current);
    }
    return null;
  }

  public boolean isExcluded(File file) {
    return PathUtil.isUnder(myExcludedRoots, file);
  }
}
