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

import org.jetbrains.org.objectweb.asm.ClassReader;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.Future;

/**
 * @author: db
 */
public class Callbacks {

  public interface Backend {
    void associate(String classFileName, String sourceFileName, ClassReader cr);
    void associate(String classFileName, Collection<String> sources, ClassReader cr);
    void registerImports(String className, Collection<String> imports, Collection<String> staticImports);
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
  }

  public interface ConstantAffectionResolver {
    Future<ConstantAffection> request(final String ownerClassName,
                                      final String fieldName,
                                      int accessFlags,
                                      boolean fieldRemoved,
                                      boolean accessChanged);
  }
}
