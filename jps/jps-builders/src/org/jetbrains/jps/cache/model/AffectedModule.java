package org.jetbrains.jps.cache.model;

import java.io.File;

public class AffectedModule {
  private final String type;
  private final String name;
  private final String hash;
  private final File outPath;

  public AffectedModule(String type, String name, String hash, File outPath) {
    this.type = type;
    this.name = name;
    this.hash = hash;
    this.outPath = outPath;
  }

  public String getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public String getHash() {
    return hash;
  }

  public File getOutPath() {
    return outPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AffectedModule module = (AffectedModule)o;

    if (type != null ? !type.equals(module.type) : module.type != null) return false;
    if (name != null ? !name.equals(module.name) : module.name != null) return false;
    if (hash != null ? !hash.equals(module.hash) : module.hash != null) return false;
    if (outPath != null ? !outPath.equals(module.outPath) : module.outPath != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = type != null ? type.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (hash != null ? hash.hashCode() : 0);
    result = 31 * result + (outPath != null ? outPath.hashCode() : 0);
    return result;
  }
}
