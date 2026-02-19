// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.cache.model;

import java.io.File;

public final class AffectedModule {
  private final String type;
  private final String name;
  private final long hash;
  private final File outPath;

  public AffectedModule(String type, String name, long hash, File outPath) {
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

  public long getHash() {
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
    if (hash != module.hash) return false;
    if (outPath != null ? !outPath.equals(module.outPath) : module.outPath != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = type != null ? type.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + Long.hashCode(hash);
    result = 31 * result + (outPath != null ? outPath.hashCode() : 0);
    return result;
  }
}
