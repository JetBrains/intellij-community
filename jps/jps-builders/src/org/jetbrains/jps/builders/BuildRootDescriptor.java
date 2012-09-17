package org.jetbrains.jps.builders;

import java.io.File;

/**
 * @author nik
 */
public abstract class BuildRootDescriptor {
  public abstract String getRootId();

  public abstract File getRootFile();

  public abstract BuildTarget getTarget();
}
