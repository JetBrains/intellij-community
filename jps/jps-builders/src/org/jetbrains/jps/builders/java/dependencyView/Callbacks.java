/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.SmartList;
import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Future;

/**
 * @author: db
 */
public class Callbacks {

  public interface ConstantRef {
    String getOwner();
    String getName();
    String getDescriptor();
  }

  public interface Backend {
    void associate(String classFileName, String sourceFileName, ClassReader cr);
    void associate(String classFileName, Collection<String> sources, ClassReader cr);
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
  public interface ConstantAffectionResolver {
    Future<ConstantAffection> request(
      final String ownerClassName, final String fieldName, int accessFlags, boolean fieldRemoved, boolean accessChanged
    );
  }
}
