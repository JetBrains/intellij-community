// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.instrumentationJavacPlugin;

import com.intellij.tools.build.bazel.jvmIncBuilder.instrumentation.InstrumentationClassFinder;
import org.jetbrains.annotations.Nullable;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The class finder of the instrumenters, over the java compiler file manager of the compile session.
 * In the multi-module mode, CLASS_OUTPUT has one location for each module. For a class without an output request, the
 * finder searches all of them. It uses the class only when exactly one module has it. When more than one module has
 * the class, the finder cannot know which one javac used, so the search ends without a class.
 */
final class FileManagerClassFinder extends InstrumentationClassFinder {
  private final JavaFileManager myFileManager;
  private final Map<String, OutputRequest> myCompiledClasses;
  private final boolean myIsPlatformModular;
  private final boolean myIsNamedModule;
  private final boolean myMultiModule;
  private final Map<String, Location> mySystemModuleOfPackage = new HashMap<>();
  private final Map<String, Location> myModulePathModuleOfPackage = new HashMap<>();
  private @Nullable List<Location> mySystemModules;
  private @Nullable List<Location> myModulePathModules;
  private @Nullable List<Location> myOutputModules;

  FileManagerClassFinder(
    JavaFileManager fileManager, Iterable<OutputRequest> compiled, boolean isPlatformModular, boolean isNamedModule, boolean isMultiModule
  ) {
    super(new URL[0]);
    myFileManager = fileManager;
    myCompiledClasses = new HashMap<>();
    for (OutputRequest cls : compiled) {
      myCompiledClasses.putIfAbsent(cls.internalName(), cls);
    }
    myIsPlatformModular = isPlatformModular;
    myIsNamedModule = isNamedModule;
    myMultiModule = isMultiModule;
  }

  @Override
  protected InputStream lookupClassBeforeClasspath(String internalClassName) {
    try {
      byte[] content = find(internalClassName);
      return content != null ? new ByteArrayInputStream(content) : null;
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private byte @Nullable [] find(String internalClassName) throws IOException {
    String binaryName = internalClassName.replace('/', '.');
    byte[] content = platformClass(binaryName);
    if (content != null) {
      return content;
    }
    OutputRequest compiledClass = myCompiledClasses.get(internalClassName);
    if (compiledClass != null) {
      return compiledClass.read(myFileManager);
    }
    List<byte[]> outputClasses = otherOutputClasses(binaryName);
    if (!outputClasses.isEmpty()) {
      // With the class in more than one module, the class that javac used is unknown, and no other class stands in for it.
      return outputClasses.size() == 1 ? outputClasses.getFirst() : null;
    }
    content = classPathClass(binaryName);
    if (content == null && myIsNamedModule && myIsPlatformModular) {
      content = modulePathClass(binaryName);
    }
    return content;
  }

  private byte @Nullable [] platformClass(String binaryName) throws IOException {
    Location location = StandardLocation.PLATFORM_CLASS_PATH;
    if (myIsPlatformModular) {
      if (mySystemModules == null) {
        mySystemModules = moduleLocations(StandardLocation.SYSTEM_MODULES);
      }
      location = moduleOf(binaryName, mySystemModules, mySystemModuleOfPackage);
    }
    return inputClass(location, binaryName);
  }

  /**
   * The content of a class in CLASS_OUTPUT without a recorded request, from each output location that has the class. In
   * the multi-module mode these are the locations of the modules in CLASS_OUTPUT, and the search stops at the second
   * module that has the class.
   */
  private List<byte[]> otherOutputClasses(String binaryName) throws IOException {
    if (!myMultiModule) {
      byte[] content = outputClass(StandardLocation.CLASS_OUTPUT, binaryName);
      return content != null ? List.of(content) : List.of();
    }
    if (myOutputModules == null) {
      myOutputModules = moduleLocations(StandardLocation.CLASS_OUTPUT);
    }
    byte[] result = null;
    for (Location location : myOutputModules) {
      byte[] content = outputClass(location, binaryName);
      if (content != null) {
        if (result != null) {
          return List.of(result, content);
        }
        result = content;
      }
    }
    return result != null? List.of(result) : List.of();
  }

  private byte @Nullable [] classPathClass(String binaryName) throws IOException {
    return inputClass(StandardLocation.CLASS_PATH, binaryName);
  }

  private byte @Nullable [] modulePathClass(String binaryName) throws IOException {
    if (!myFileManager.hasLocation(StandardLocation.MODULE_PATH)) {
      return null;
    }
    if (myModulePathModules == null) {
      myModulePathModules = moduleLocations(StandardLocation.MODULE_PATH);
    }
    Location location = moduleOf(binaryName, myModulePathModules, myModulePathModuleOfPackage);
    return inputClass(location, binaryName);
  }

  private byte @Nullable [] inputClass(@Nullable Location location, String binaryName) throws IOException {
    try {
      return location == null? null : read(myFileManager.getJavaFileForInput(location, binaryName, JavaFileObject.Kind.CLASS));
    }
    catch (FileNotFoundException | NoSuchFileException e) {
      // JpsJavacFileManager throws for a missing class, where the JavaFileManager contract allows null.
      return null;
    }
  }

  private byte @Nullable [] outputClass(@Nullable Location location, String binaryName) throws IOException {
    try {
      return location == null? null : read(myFileManager.getJavaFileForOutput(location, binaryName, JavaFileObject.Kind.CLASS, null));
    }
    catch (FileNotFoundException | NoSuchFileException e) {
      // JpsJavacFileManager throws for a missing class, where the JavaFileManager contract allows null.
      return null;
    }
  }

  private List<Location> moduleLocations(Location moduleOrientedLocation) throws IOException {
    List<Location> result = new ArrayList<>();
    for (Set<Location> locations : myFileManager.listLocationsForModules(moduleOrientedLocation)) {
      result.addAll(locations);
    }
    return result;
  }

  /**
   * The module location that holds the package of the class, found with one list call per module location.
   */
  private @Nullable Location moduleOf(String binaryName, List<Location> moduleLocations, Map<String, Location> moduleOfPackage)
    throws IOException {
    int lastDot = binaryName.lastIndexOf('.');
    String packageName = lastDot < 0 ? "" : binaryName.substring(0, lastDot);
    if (moduleOfPackage.containsKey(packageName)) {
      return moduleOfPackage.get(packageName);
    }
    Location found = null;
    for (Location location : moduleLocations) {
      if (myFileManager.list(location, packageName, EnumSet.of(JavaFileObject.Kind.CLASS), false).iterator().hasNext()) {
        found = location;
        break;
      }
    }
    moduleOfPackage.put(packageName, found);
    return found;
  }

  private static byte @Nullable [] read(@Nullable JavaFileObject file) throws IOException {
    if (file == null) {
      return null;
    }
    try (InputStream in = file.openInputStream()) {
      return in.readAllBytes();
    }
  }

  @Override
  public void releaseResources() {
    super.releaseResources();
    myCompiledClasses.clear();
  }
}
