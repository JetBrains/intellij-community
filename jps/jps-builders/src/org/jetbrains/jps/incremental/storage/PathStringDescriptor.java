package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;

/**
* @author nik
*/
public class PathStringDescriptor extends EnumeratorStringDescriptor {
  @Override
  public int getHashCode(String value) {
    return FileUtil.pathHashCode(value);
  }

  @Override
  public boolean isEqual(String val1, String val2) {
    return FileUtil.pathsEqual(val1, val2);
  }
}
