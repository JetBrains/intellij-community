/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.compiler.ant;

import com.intellij.compiler.ant.taskdefs.Path;
import com.intellij.compiler.ant.taskdefs.PathElement;
import com.intellij.compiler.ant.taskdefs.PathRef;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.OrderedSet;
import gnu.trove.TObjectHashingStrategy;

import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 22, 2004
 */
public class ModuleChunkClasspath extends Path{

  public ModuleChunkClasspath(ModuleChunk chunk, final GenerationOptions genOptions) {
    super(BuildProperties.getClasspathProperty(chunk.getName()));

    final OrderedSet<ClasspathItem> pathItems = new OrderedSet<ClasspathItem>((TObjectHashingStrategy<ClasspathItem>)TObjectHashingStrategy.CANONICAL);
    final String compilerOutputPathUrl = chunk.getOutputDirUrl();
    final String compilerOutputPathForTestsUrl = chunk.getTestsOutputDirUrl();
    final String moduleChunkBasedirProperty = BuildProperties.getModuleChunkBasedirProperty(chunk);
    final Module[] modules = chunk.getModules();
    for (int moduleIdx = 0; moduleIdx < modules.length; moduleIdx++) {
      final Module module = modules[moduleIdx];
      final OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
      for (int idx = 0; idx < orderEntries.length; idx++) {
        final OrderEntry orderEntry = orderEntries[idx];
        if (!orderEntry.isValid()) {
          continue;
        }
        if (orderEntry instanceof JdkOrderEntry) {
          if (genOptions.forceTargetJdk) {
            pathItems.add(new PathRefItem(BuildProperties.propertyRef(BuildProperties.getModuleChunkJdkClasspathProperty(chunk.getName()))));
          }
        }
        else if (orderEntry instanceof LibraryOrderEntry && !((LibraryOrderEntry)orderEntry).isModuleLevel()) {
          final String libraryName = ((LibraryOrderEntry)orderEntry).getLibraryName();
          pathItems.add(new PathRefItem(BuildProperties.getLibraryPathId(libraryName)));
        }
        else {
          final String[] files = orderEntry.getUrls(OrderRootType.COMPILATION_CLASSES);
          for (int i = 0; i < files.length; i++) {
            String url = files[i];
            if (url.endsWith(JarFileSystem.JAR_SEPARATOR)) {
              url = url.substring(0, url.length() - JarFileSystem.JAR_SEPARATOR.length());
            }
            if (compilerOutputPathUrl != null) {
              if (url.equals(compilerOutputPathUrl)) {
                continue;
              }
            }
            if (compilerOutputPathForTestsUrl != null) {
              if (url.equals(compilerOutputPathForTestsUrl)) {
                continue;
              }
            }
            final String propertyRef = genOptions.getPropertyRefForUrl(url);
            if (propertyRef != null) {
              pathItems.add(new PathElementItem(propertyRef));
            }
            else {
              final String path = VirtualFileManager.extractPath(url);
              pathItems.add(new PathElementItem(GenerationUtils.toRelativePath(path, chunk.getBaseDir(), moduleChunkBasedirProperty, genOptions, !chunk.isSavePathsRelative())));
            }
          }
        }
      }
    }
    for (Iterator<ClasspathItem> it = pathItems.iterator(); it.hasNext();) {
      add(it.next().toGenerator());
    }
  }

  private abstract static class ClasspathItem {
    protected final String myValue;

    public ClasspathItem(String value) {
      myValue = value;
    }

    public abstract Generator toGenerator();
  }

  private static class PathElementItem extends ClasspathItem {
    public PathElementItem(String value) {
      super(value);
    }

    public Generator toGenerator() {
      return new PathElement(myValue);
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PathElementItem)) return false;

      final PathElementItem pathElementItem = (PathElementItem)o;

      if (!myValue.equals(pathElementItem.myValue)) return false;

      return true;
    }

    public int hashCode() {
      return myValue.hashCode();
    }
  }

  private static class PathRefItem extends ClasspathItem {
    public PathRefItem(String value) {
      super(value);
    }

    public Generator toGenerator() {
      return new PathRef(myValue);
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PathRefItem)) return false;

      final PathRefItem pathRefItem = (PathRefItem)o;

      if (!myValue.equals(pathRefItem.myValue)) return false;

      return true;
    }

    public int hashCode() {
      return myValue.hashCode();
    }
  }
}
