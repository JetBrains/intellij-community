package com.intellij.tools.build.bazel.jvmIncBuilder;

import java.io.Closeable;
import java.io.IOException;

public interface CloseableExt extends Closeable {

  void close(boolean saveChanges) throws IOException;
  
}
