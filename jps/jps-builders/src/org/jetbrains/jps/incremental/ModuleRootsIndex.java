package org.jetbrains.jps.incremental;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.builders.AdditionalRootsProviderService;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/11/12
 */
public class ModuleRootsIndex {
  private final THashMap<File, RootDescriptor> myRootToDescriptorMap = new THashMap<File, RootDescriptor>(FileUtil.FILE_HASHING_STRATEGY);
  private final Map<JpsModule, List<RootDescriptor>> myModuleToRootsMap = new HashMap<JpsModule, List<RootDescriptor>>();
  private final Map<String, JpsModule> myNameToModuleMap = new HashMap<String, JpsModule>();
  private final int myTotalModuleCount;
  private final Set<File> myExcludedRoots = new HashSet<File>();
  private final Map<JpsModule, List<File>> myModuleToExcludesMap = new HashMap<JpsModule, List<File>>();
  private final IgnoredFilePatterns myIgnoredFilePatterns;

  private static final Key<Map<File, RootDescriptor>> ROOT_DESCRIPTOR_MAP = Key.create("_root_to_descriptor_map");
  private static final Key<Map<JpsModule, List<RootDescriptor>>> MODULE_ROOT_MAP = Key.create("_module_to_root_map");

  public ModuleRootsIndex(JpsModel model, File dataStorageRoot) {
    myIgnoredFilePatterns = new IgnoredFilePatterns(model.getGlobal().getFileTypesConfiguration().getIgnoredPatternString());
    final Collection<JpsModule> allModules = model.getProject().getModules();
    myTotalModuleCount = allModules.size();
    final Iterable<AdditionalRootsProviderService> rootsProviders = JpsServiceManager.getInstance().getExtensions(AdditionalRootsProviderService.class);
    for (final JpsModule module : allModules) {
      final String moduleName = module.getName();

      myNameToModuleMap.put(moduleName, module);
      final List<File> moduleExcludes = new ArrayList<File>();
      myModuleToExcludesMap.put(module, moduleExcludes);
      List<RootDescriptor> moduleRoots = myModuleToRootsMap.get(module);
      if (moduleRoots == null) {
        moduleRoots = new ArrayList<RootDescriptor>();
        myModuleToRootsMap.put(module, moduleRoots);
      }
      for (JpsModuleSourceRoot sourceRoot : module.getSourceRoots()) {
        final File root = JpsPathUtil.urlToFile(sourceRoot.getUrl());
        final boolean testRoot = JavaSourceRootType.TEST_SOURCE.equals(sourceRoot.getRootType());
        final RootDescriptor descriptor = new RootDescriptor(root, new ModuleBuildTarget(module, JavaModuleBuildTargetType.getInstance(testRoot)), testRoot, false, false);
        myRootToDescriptorMap.put(root, descriptor);
        moduleRoots.add(descriptor);
      }
      for (AdditionalRootsProviderService provider : rootsProviders) {
        final List<String> roots = provider.getAdditionalSourceRoots(module, dataStorageRoot);
        for (String path : roots) {
          File root = new File(path);
          final RootDescriptor descriptor = new RootDescriptor(root, new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION), false, true, false);
          moduleRoots.add(descriptor);
          myRootToDescriptorMap.put(root, descriptor);
        }
      }
      for (String url : module.getExcludeRootsList().getUrls()) {
        final File root = JpsPathUtil.urlToFile(url);
        myExcludedRoots.add(root);
        moduleExcludes.add(root);
      }
    }

    Map<File, JpsModule> contentToModule = new HashMap<File, JpsModule>();
    for (JpsModule module : allModules) {
      for (String contentUrl : module.getContentRootsList().getUrls()) {
        File contentRoot = JpsPathUtil.urlToFile(contentUrl);
        contentToModule.put(contentRoot, module);
      }
    }
    List<File> parents = new ArrayList<File>();
    for (JpsModule module : allModules) {
      for (String contentUrl : module.getContentRootsList().getUrls()) {
        File contentRoot = JpsPathUtil.urlToFile(contentUrl);
        File parent = contentRoot.getParentFile();
        JpsModule parentModule = null;
        parents.clear();
        while (parent != null) {
          parents.add(parent);
          if (contentToModule.containsKey(parent)) {
            parentModule = contentToModule.get(parent);
            break;
          }
          parent = parent.getParentFile();
        }
        if (parentModule != null) {
          myModuleToExcludesMap.get(parentModule).add(contentRoot);
        }
        for (File file : parents) {
          contentToModule.put(file, parentModule);
        }
      }
    }
  }

  public IgnoredFilePatterns getIgnoredFilePatterns() {
    return myIgnoredFilePatterns;
  }

  public int getTotalModuleCount() {
    return myTotalModuleCount;
  }

  @Nullable
  public JpsModule getModuleByName(String module) {
    return myNameToModuleMap.get(module);
  }

  @NotNull
  public List<RootDescriptor> getModuleRoots(@Nullable CompileContext context, JpsModule module) {
    List<RootDescriptor> descriptors = myModuleToRootsMap.get(module);
    if (context != null) {
      final Map<JpsModule, List<RootDescriptor>> contextMap = MODULE_ROOT_MAP.get(context);
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
  public RootDescriptor associateRoot(@NotNull CompileContext context, File root, JpsModule module, boolean isTestRoot) {
    Map<File, RootDescriptor> rootToDescriptorMap = ROOT_DESCRIPTOR_MAP.get(context);
    if (rootToDescriptorMap == null) {
      rootToDescriptorMap = new THashMap<File, RootDescriptor>(FileUtil.FILE_HASHING_STRATEGY);
      ROOT_DESCRIPTOR_MAP.set(context, rootToDescriptorMap);
    }

    Map<JpsModule, List<RootDescriptor>> moduleToRootMap = MODULE_ROOT_MAP.get(context);
    if (moduleToRootMap == null) {
      moduleToRootMap = new HashMap<JpsModule, List<RootDescriptor>>();
      MODULE_ROOT_MAP.set(context, moduleToRootMap);
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
    final RootDescriptor descriptor = new RootDescriptor(root, new ModuleBuildTarget(module, JavaModuleBuildTargetType.getInstance(isTestRoot)), isTestRoot, true, true);
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
    return JpsPathUtil.isUnder(myExcludedRoots, file);
  }

  public Collection<File> getModuleExcludes(JpsModule module) {
    return myModuleToExcludesMap.get(module);
  }
}
