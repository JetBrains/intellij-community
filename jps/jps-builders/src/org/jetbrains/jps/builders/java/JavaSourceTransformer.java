package org.jetbrains.jps.builders.java;

import java.io.File;
import java.io.IOException;

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
}
