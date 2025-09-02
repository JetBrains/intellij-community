// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import java.io.File;
import java.util.*;

public abstract class ModulePath {
  public interface Builder {
    Builder add(String moduleName, File pathElement);
    ModulePath create();
  }

  public abstract Collection<? extends File> getPath();

  /**
   * @param pathElement a single module path entry
   * @return a JPMS module name associated with the passed module path element.
   *   null value does not necessarily mean the entry cannot be treated as a JPMS module. null only
   *   means that there is no module name information stored for the file in this ModulePath object
   */
  public abstract String getModuleName(File pathElement);

  public boolean isEmpty() {
    return getPath().isEmpty();
  }

  public static final ModulePath EMPTY = new ModulePath() {
    @Override
    public Collection<? extends File> getPath() {
      return Collections.emptyList();
    }

    @Override
    public String getModuleName(File pathElement) {
      return null;
    }
  };

  public static ModulePath create(Collection<? extends File> path) {
    if (path.isEmpty()) {
      return EMPTY;
    }
    final Collection<File> files = Collections.unmodifiableCollection(path);
    return new ModulePath() {
      @Override
      public Collection<? extends File> getPath() {
        return files;
      }

      @Override
      public String getModuleName(File pathElement) {
        return null;
      }
    };
  }

  public static Builder newBuilder() {
    return new Builder() {
      private final Map<File, String> myMap = new HashMap<>();
      private final Collection<File> myPath = new ArrayList<>();

      @Override
      public Builder add(String moduleName, File pathElement) {
        myPath.add(pathElement);
        if (moduleName != null) {
          myMap.put(pathElement, moduleName);
        }
        return this;
      }

      @Override
      public ModulePath create() {
        if (myPath.isEmpty()) {
          return EMPTY;
        }
        final Collection<File> files = Collections.unmodifiableCollection(myPath);
        return new ModulePath() {
          @Override
          public Collection<? extends File> getPath() {
            return files;
          }

          @Override
          public String getModuleName(File pathElement) {
            return myMap.get(pathElement);
          }
        };
      }
    };
  }

}
