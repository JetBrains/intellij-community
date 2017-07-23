package org.jetbrains.jps.builders.java;

import org.jetbrains.jps.ExtensionsSupport;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/7/13
 */
public abstract class JavaSourceTransformer {
  
  public static abstract class TransformError extends IOException {
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

  private static final ExtensionsSupport<JavaSourceTransformer> ourExtSupport = new ExtensionsSupport<JavaSourceTransformer>(JavaSourceTransformer.class);

  public static Collection<JavaSourceTransformer> getTransformers() {
    return ourExtSupport.getExtensions();
  }
}
