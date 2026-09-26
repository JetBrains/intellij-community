// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

import org.jetbrains.jps.ExtensionsSupport;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public abstract class JavaSourceTransformer {
  
  public abstract static class TransformError extends IOException {
    protected TransformError(String message) {
      super(message);
    }

    protected TransformError(String message, Throwable cause) {
      super(message, cause);
    }

    protected TransformError(Throwable cause) {
      super(cause);
    }
  } 
  
  
  public abstract CharSequence transform(File sourceFile, CharSequence content) throws TransformError;

  private static final ExtensionsSupport<JavaSourceTransformer> ourExtSupport = new ExtensionsSupport<>(JavaSourceTransformer.class);

  public static Collection<JavaSourceTransformer> getTransformers() {
    return ourExtSupport.getExtensions();
  }
}
