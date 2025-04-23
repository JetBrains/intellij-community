// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.runner;

import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.Usage;

import java.util.Collection;

public interface CompilerDataSink {

  interface ConstantRef {
    String getOwner();
    String getName();
    String getDescriptor();

    static ConstantRef create(String ownerClass, String fieldName, String descriptor) {
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
  }

  void registerImports(String className, Collection<String> classImports, Collection<String> staticImports);

  void registerConstantReferences(String className, Collection<ConstantRef> cRefs);

  default void registerUsage(String className, Usage usage) {
  }

  default void registerUsage(NodeSource source, Usage usage) {
  }

}
