// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.java;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.library.OrderedClassLibraryBuilder;
import com.thoughtworks.qdox.model.JavaModule;
import com.thoughtworks.qdox.model.JavaModuleDescriptor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.javac.JpsJavacFileManager;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 * Date: 26-Sep-19
 */
public class ModulePathSplitter {

  private final Map<File, ModuleInfo> myCache = Collections.synchronizedMap(new THashMap<>(FileUtil.FILE_HASHING_STRATEGY));
  private static final Method myModuleFinderCreateMethod;
  private static final Method myFindAll;
  private static final Method myGetDescriptor;
  private static final Method myDescriptorName;
  private static final Method myRequiresName;
  private static final Method myDescriptorRequires;

  static {
    Method of = null;
    Method findAll = null;
    Method getDescriptor = null;
    Method descriptorName = null;
    Method requiresName = null;
    Method descriptorRequires = null;
    try {
      final Class<?> finderClass = Class.forName("java.lang.module.ModuleFinder");
      final Class<?> descriptorClass = Class.forName("java.lang.module.ModuleDescriptor");
      final Class<?> referenceClass = Class.forName("java.lang.module.ModuleReference");
      final Class<?> requireClass = Class.forName("java.lang.module.ModuleDescriptor$Requires");
      of = finderClass.getDeclaredMethod("of", Path[].class);
      findAll = finderClass.getDeclaredMethod("findAll");
      getDescriptor = referenceClass.getDeclaredMethod("descriptor");
      descriptorName = descriptorClass.getDeclaredMethod("name");
      descriptorRequires = descriptorClass.getDeclaredMethod("requires");
      requiresName = requireClass.getDeclaredMethod("name");
    }
    catch (Throwable ignored) {
    }
    myModuleFinderCreateMethod = of;
    myFindAll = findAll;
    myGetDescriptor = getDescriptor;
    myDescriptorName = descriptorName;
    myRequiresName = requiresName;
    myDescriptorRequires = descriptorRequires;
  }

  public ModulePathSplitter() {
  }

  public Pair<Collection<File>, Collection<File>> splitPath(File chunkModuleInfo, Set<File> chunkOutputs, Collection<File> path) {
    if (myModuleFinderCreateMethod == null) {
      // the module API is not available
      return Pair.create(path, Collections.emptyList());
    }
    final List<File> modulePath = new ArrayList<>();
    final List<File> classpath = new ArrayList<>();

    final Set<String> allRequired = collectRequired(chunkModuleInfo, JpsJavacFileManager.filter(path, file -> !chunkOutputs.contains(file)));
    for (File file : path) {
      (chunkOutputs.contains(file) || allRequired.contains(getModuleInfo(file).name) ? modulePath : classpath).add(file);
    }
    return Pair.create(Collections.unmodifiableList(modulePath), Collections.unmodifiableList(classpath));
  }

  private Set<String> collectRequired(File chunkModuleInfo, Iterable<? extends File> path) {
    final Set<String> result = new HashSet<>();
    // first, add all requires from chunk module-info
    final JavaModuleDescriptor chunkDescr = new JavaProjectBuilder(new OrderedClassLibraryBuilder()).addSourceFolder(chunkModuleInfo.getParentFile()).getDescriptor();
    for (JavaModuleDescriptor.JavaRequires require : chunkDescr.getRequires()) {
      final JavaModule rm = require.getModule();
      if (rm != null) {
        result.add(rm.getName());
      }
    }
    for (File file : path) {
      result.addAll(getModuleInfo(file).requires);
    }
    return result;
  }

  @NotNull
  private ModuleInfo getModuleInfo(File f) {
    ModuleInfo info = myCache.get(f);
    if (info != null) {
      return info;
    }
    info = ModuleInfo.EMPTY;

    try {
      Object mf = myModuleFinderCreateMethod.invoke(null, (Object)new Path[]{f.toPath()}); //  ModuleFinder.of(f.toPath());
      for (Object moduleRef : (Set)myFindAll.invoke(mf)) {  // mf.findAll()
        final Object descriptor = myGetDescriptor.invoke(moduleRef); // moduleRef.descriptor()
        final String moduleName = (String)myDescriptorName.invoke(descriptor); // descriptor.name();
        final Set requires = (Set)myDescriptorRequires.invoke(descriptor); //descriptor.requires();
        if (requires.isEmpty()) {
          info = new ModuleInfo(moduleName);
        }
        else {
          final Set<String> req = new HashSet<>();
          for (Object require : requires) {
            req.add((String)myRequiresName.invoke(require)/*require.name()*/);
          }
          info = new ModuleInfo(moduleName, req);
        }
        break;
      }
    }
    catch (Throwable ignored) {
    }
    myCache.put(f, info);
    return info;
  }

  private static final class ModuleInfo {
    static final ModuleInfo EMPTY = new ModuleInfo(null, Collections.emptyList());

    @Nullable
    final String name;
    @NotNull
    final Collection<String> requires;

    ModuleInfo(String name) {
      this(name, Collections.emptyList());
    }

    ModuleInfo(@Nullable String name, @NotNull Collection<String> requires) {
      this.name = name;
      this.requires = Collections.unmodifiableCollection(requires);
    }
  }
}
