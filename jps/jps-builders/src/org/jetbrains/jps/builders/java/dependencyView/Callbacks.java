// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Future;

/**
 * @author: db
 */
public final class Callbacks {

  public interface ConstantRef {
    String getOwner();
    String getName();
    String getDescriptor();
  }

  public interface Backend {
    default void associate(String classFileName, String sourceFileName, ClassReader cr) {
      associate(classFileName, Collections.singleton(sourceFileName), cr);
    }
    default void associate(String classFileName, Collection<String> sources, ClassReader cr) {
      associate(classFileName, sources, cr, false);
    }
    void associate(String classFileName, Collection<String> sources, ClassReader cr, boolean isGenerated);
    void registerImports(String className, Collection<String> classImports, Collection<String> staticImports);
    void registerConstantReferences(String className, Collection<ConstantRef> cRefs);
  }

  public static ConstantRef createConstantReference(String ownerClass, String fieldName, String descriptor) {
    return new ConstantRef() {
      @Override
      public String getOwner() {
        return ownerClass;
      }

      @Override
      public String getName() {
        return fieldName;
      }

      @Override
      public String getDescriptor() {
        return descriptor;
      }
    };
  }

  public static class ConstantAffection {
    public static final ConstantAffection EMPTY = new ConstantAffection();
    private final boolean myKnown;
    private final Collection<File> myAffectedFiles;

    public ConstantAffection(final Collection<File> affectedFiles) {
      myAffectedFiles = affectedFiles;
      myKnown = true;
    }

    public ConstantAffection() {
      myKnown = false;
      myAffectedFiles = null;
    }

    public boolean isKnown(){
      return myKnown;
    }

    public Collection<File> getAffectedFiles (){
      return myAffectedFiles;
    }

    public static ConstantAffection compose(final Collection<? extends ConstantAffection> affections) {
      if (affections.isEmpty()) {
        return new ConstantAffection(Collections.emptyList()); // return a 'known' affection here
      }
      if (affections.size() == 1) {
        return affections.iterator().next();
      }
      for (ConstantAffection a : affections) {
        if (!a.isKnown()) {
          return EMPTY;
        }
      }
      final Collection<File> affected = new SmartList<>();
      for (ConstantAffection affection : affections) {
        affected.addAll(affection.getAffectedFiles());
      }
      return new ConstantAffection(affected);
    }
  }

  /**
   * @deprecated This functionality is obsolete and is not used by dependency analysis anymore.
   * To be removed in later releases
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.3")
  public interface ConstantAffectionResolver {
    Future<ConstantAffection> request(
      final String ownerClassName, final String fieldName, int accessFlags, boolean fieldRemoved, boolean accessChanged
    );
  }
}
