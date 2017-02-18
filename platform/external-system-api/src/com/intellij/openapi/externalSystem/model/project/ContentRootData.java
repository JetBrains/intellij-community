package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 8/9/11 6:25 PM
 */
public class ContentRootData extends AbstractExternalEntityData {

  private static final long serialVersionUID = 1L;

  @NotNull private final Map<ExternalSystemSourceType, Collection<SourceRoot>> myData = ContainerUtilRt.newHashMap();

  @NotNull private final String myRootPath;

  /**
   * Creates new <code>GradleContentRootImpl</code> object.
   *
   * @param rootPath  path to the root directory
   */
  public ContentRootData(@NotNull ProjectSystemId owner, @NotNull String rootPath) {
    super(owner);
    myRootPath = ExternalSystemApiUtil.toCanonicalPath(rootPath);
  }

  /**
   * @param type      target dir type
   * @return          directories of the target type configured for the current content root
   */
  @NotNull
  public Collection<SourceRoot> getPaths(@NotNull ExternalSystemSourceType type) {
    final Collection<SourceRoot> result = myData.get(type);
    return result == null ? Collections.<SourceRoot>emptyList() : result;
  }

  public void storePath(@NotNull ExternalSystemSourceType type, @NotNull String path) throws IllegalArgumentException {
    storePath(type, path, null);
  }

  /**
   * Ask to remember that directory at the given path contains sources of the given type.
   *
   * @param type           target sources type
   * @param path           target source directory path
   * @param packagePrefix  target source directory package prefix
   * @throws IllegalArgumentException   if given path points to the directory that is not located
   *                                    under the {@link #getRootPath() content root}
   */
  public void storePath(@NotNull ExternalSystemSourceType type, @NotNull String path, @Nullable String packagePrefix) throws IllegalArgumentException {
    if (FileUtil.isAncestor(new File(getRootPath()), new File(path), false)) {
      Collection<SourceRoot> paths = myData.get(type);
      if (paths == null) {
        myData.put(type, paths = new TreeSet<>(SourceRootComparator.INSTANCE));
      }
      paths.add(new SourceRoot(
        ExternalSystemApiUtil.toCanonicalPath(path),
        StringUtil.nullize(packagePrefix, true)
      ));
      return;
    }
    if (!ExternalSystemSourceType.EXCLUDED.equals(type)) { // There are external systems which mark output directory as 'excluded' path.
      // We don't need to bother if it's outside a module content root then.
      throw new IllegalArgumentException(String.format(
        "Can't register given path of type '%s' because it's out of content root.%nContent root: '%s'%nGiven path: '%s'",
        type, getRootPath(), new File(path).getAbsolutePath()
      ));
    }
  }

  @NotNull
  public String getRootPath() {
    return myRootPath;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder("content root:");
    for (Map.Entry<ExternalSystemSourceType, Collection<SourceRoot>> entry : myData.entrySet()) {
      buffer.append(entry.getKey().toString().toLowerCase(Locale.ENGLISH)).append("=").append(entry.getValue()).append("|");
    }
    if (!myData.isEmpty()) {
      buffer.setLength(buffer.length() - 1);
    }
    return buffer.toString();
  }

  public static class SourceRoot implements Serializable {
    @NotNull
    private final String myPath;

    @Nullable
    private final String myPackagePrefix;

    public SourceRoot(@NotNull String path, @Nullable String prefix) {
      myPath = path;
      myPackagePrefix = prefix;
    }

    @NotNull
    public String getPath() {
      return myPath;
    }

    @Nullable
    public String getPackagePrefix() {
      return myPackagePrefix;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SourceRoot)) return false;
      SourceRoot root = (SourceRoot)o;
      if (myPackagePrefix != null ? !myPackagePrefix.equals(root.myPackagePrefix) : root.myPackagePrefix != null) return false;
      if (!myPath.equals(root.myPath)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = myPath.hashCode();
      result = 31 * result + (myPackagePrefix != null ? myPackagePrefix.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      StringBuilder buffer = new StringBuilder("source_root(");
      buffer.append(myPath);
      if (myPackagePrefix != null) {
        buffer.append(", ").append(myPackagePrefix);
      }
      buffer.append(")");
      return buffer.toString();
    }
  }

  private static final class SourceRootComparator implements Comparator<SourceRoot>, Serializable {
    private static final SourceRootComparator INSTANCE = new SourceRootComparator();

    @Override
    public int compare(@NotNull SourceRoot o1, @NotNull SourceRoot o2) {
      return StringUtil.naturalCompare(o1.myPath, o2.myPath);
    }
  }
}
