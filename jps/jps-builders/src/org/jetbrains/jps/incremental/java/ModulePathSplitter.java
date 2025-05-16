// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.java;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.FileCollectionFactory;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.library.OrderedClassLibraryBuilder;
import com.thoughtworks.qdox.model.JavaModule;
import com.thoughtworks.qdox.model.JavaModuleDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.javac.ModulePath;
import org.jetbrains.jps.util.Iterators;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 */
public final class ModulePathSplitter {
  private final Map<File, ModuleInfo> myCache = Collections.synchronizedMap(FileCollectionFactory.createCanonicalFileMap());
  private static final Attributes.Name AUTOMATIC_MODULE_NAME = new Attributes.Name("Automatic-Module-Name");
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

  // derives module name from filename
  public static final Function<File, String> DEFAULT_MODULE_NAME_SEARCH = file -> {
    final String fName = file.getName();
    final int dotIndex = fName.lastIndexOf('.'); // drop extension
    return dotIndex >= 0? fName.substring(0, dotIndex) : fName;
  };

  private final @NotNull Function<? super File, String> myModuleNameSearch;

  public ModulePathSplitter() {
    this(DEFAULT_MODULE_NAME_SEARCH);
  }

  public ModulePathSplitter(@NotNull Function<? super File, String> moduleNameSearch) {
    myModuleNameSearch = moduleNameSearch;
  }

  public Pair<ModulePath, Collection<File>> splitPath(@Nullable File chunkModuleInfo, Set<? extends File> chunkOutputs, Collection<? extends File> path) {
    return splitPath(chunkModuleInfo, chunkOutputs, path, Collections.emptySet());
  }

  public Pair<ModulePath, Collection<File>> splitPath(@Nullable File chunkModuleInfo, Set<? extends File> chunkOutputs, Collection<? extends File> path, Collection<String> addReads) {
    if (myModuleFinderCreateMethod == null) {
      // the module API is not available
      return Pair.create(ModulePath.create(path), Collections.emptyList());
    }
    final ModulePath.Builder mpBuilder = ModulePath.newBuilder();
    final List<File> classpath = new ArrayList<>();

    final Set<String> allRequired = collectRequired(chunkModuleInfo, Iterators.filter(path, file -> !chunkOutputs.contains(file)));
    allRequired.addAll(addReads);
    for (File file : path) {
      if (chunkOutputs.contains(file)) {
        mpBuilder.add(null, file);
      }
      else {
        final ModuleInfo info = getModuleInfo(file);
        if (allRequired.contains(info.name)) {
          // storing only names for automatic modules in "exploded" form.
          // for all other kinds of roots module-name is correctly determined by javac itself
          mpBuilder.add(info.isAutomaticExploded? info.name : null, file);
        }
        else {
          classpath.add(file);
        }
      }
    }
    return Pair.create(mpBuilder.create(), Collections.unmodifiableList(classpath));
  }

  private static final Pattern NON_ALPHANUM = Pattern.compile("[^A-Za-z0-9]");
  private static final Pattern REPEATING_DOTS = Pattern.compile("(\\.)(\\1)+");

  /**
   * Following the logic of jdk.internal.module.ModulePath.cleanModuleName() that normalizes the module name derived from a jar artifact name
   */
  private static String normalizeModuleName(String fName) {
    if (fName != null) {
      fName = NON_ALPHANUM.matcher(fName).replaceAll(".");
      // collapse repeating dots
      fName = REPEATING_DOTS.matcher(fName).replaceAll(".");
      // drop leading and trailing dots
      final int len = fName.length();
      if (len > 0) {
        final int start = fName.startsWith(".") ? 1 : 0;
        final int end = fName.endsWith(".") ? len - 1 : len;
        if (start > 0 || end < len) {
          fName = fName.substring(start, end);
        }
      }
    }
    return fName;
  }

  private Set<String> collectRequired(@Nullable File chunkModuleInfo, Iterable<? extends File> path) {
    final Set<String> result = new HashSet<>();
    if (chunkModuleInfo != null) {
      // first, add all requires from chunk module-info
      final JavaModuleDescriptor chunkDescr = new JavaProjectBuilder(new OrderedClassLibraryBuilder()).addSourceFolder(chunkModuleInfo.getParentFile()).getDescriptor();
      for (JavaModuleDescriptor.JavaRequires require : chunkDescr.getRequires()) {
        final JavaModule rm = require.getModule();
        if (rm != null) {
          result.add(rm.getName());
        }
      }
    }
    for (File file : path) {
      result.addAll(getModuleInfo(file).requires);
    }
    return result;
  }

  private @NotNull ModuleInfo getModuleInfo(File f) {
    ModuleInfo info = myCache.get(f);
    if (info != null) {
      return info;
    }
    info = ModuleInfo.EMPTY;

    try {
      Object mf = myModuleFinderCreateMethod.invoke(null, (Object)new Path[]{f.toPath()}); //  ModuleFinder.of(f.toPath());
      final Set<?> moduleRefs = (Set<?>)myFindAll.invoke(mf); // mf.findAll()
      if (!moduleRefs.isEmpty()) {
        for (Object moduleRef : moduleRefs) {
          final Object descriptor = myGetDescriptor.invoke(moduleRef); // moduleRef.descriptor()
          final String moduleName = (String)myDescriptorName.invoke(descriptor); // descriptor.name();
          final Set<?> requires = (Set<?>)myDescriptorRequires.invoke(descriptor); //descriptor.requires();
          if (requires.isEmpty()) {
            info = new ModuleInfo(moduleName, false);
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
      else {
        final String explodedModuleName = deriveAutomaticModuleName(f);
        if (explodedModuleName != null) {
          info = new ModuleInfo(explodedModuleName, true);
        }
      }
    }
    catch (Throwable ignored) {
    }
    myCache.put(f, info);
    return info;
  }

  private String deriveAutomaticModuleName(File dir) {
    if (dir.isDirectory()) {
      try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(new File(dir, "META-INF/MANIFEST.MF")))) {
        final String name = new Manifest(is).getMainAttributes().getValue(AUTOMATIC_MODULE_NAME);
        return name != null ? name : normalizeModuleName(myModuleNameSearch.apply(dir));
      }
      catch (FileNotFoundException e) {
        return normalizeModuleName(myModuleNameSearch.apply(dir)); // inferring the module name from the dir
      }
      catch (Throwable ignored) {
      }
    }
    return null;
  }

  private static final class ModuleInfo {
    static final ModuleInfo EMPTY = new ModuleInfo(null, false);
    final @Nullable String name;
    final @NotNull Collection<String> requires;
    private final boolean isAutomaticExploded;

    ModuleInfo(@Nullable String name, boolean isAutomaticExploded) {
      this.name = name;
      this.requires = Collections.emptyList();
      this.isAutomaticExploded = isAutomaticExploded;
    }

    ModuleInfo(@Nullable String name, @NotNull Collection<String> requires) {
      this.name = name;
      this.requires = Collections.unmodifiableCollection(requires);
      this.isAutomaticExploded = false;
    }
  }
}
