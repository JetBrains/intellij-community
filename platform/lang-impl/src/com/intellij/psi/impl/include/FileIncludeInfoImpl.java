package com.intellij.psi.impl.include;

import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
class FileIncludeInfoImpl extends FileIncludeInfo {

  public final String providerId;

  public FileIncludeInfoImpl(@NotNull String path, int offset, boolean runtimeOnly, String providerId) {
    super("", path, offset, runtimeOnly);
    this.providerId = providerId;
  }

  @SuppressWarnings({"RedundantIfStatement"})
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FileIncludeInfoImpl that = (FileIncludeInfoImpl)o;

    if (!fileName.equals(that.fileName)) return false;
    if (!path.equals(that.path)) return false;
    if (offset != that.offset) return false;
    if (runtimeOnly != that.runtimeOnly) return false;
    if (!providerId.equals(that.providerId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = fileName.hashCode();
    result = 31 * result + path.hashCode();
    result = 31 * result + offset;
    result = 31 * result + (runtimeOnly ? 1 : 0);
    return result;
  }
}
