package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 8/9/11 6:25 PM
 */
public class ContentRootData extends AbstractExternalEntityData {

  private static final long serialVersionUID = 1L;

  @NotNull private final Map<ExternalSystemSourceType, Collection<String>> myData = ContainerUtilRt.newHashMap();

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
  public Collection<String> getPaths(@NotNull ExternalSystemSourceType type) {
    Collection<String> result = myData.get(type);
    return result == null ? Collections.<String>emptyList() : result;
  }

  /**
   * Ask to remember that directory at the given path contains sources of the given type.
   *
   * @param type  target sources type
   * @param path  target source directory path
   * @throws IllegalArgumentException   if given path points to the directory that is not located
   *                                    under the {@link #getRootPath() content root}
   */
  public void storePath(@NotNull ExternalSystemSourceType type, @NotNull String path) throws IllegalArgumentException {
    if (FileUtil.isAncestor(new File(getRootPath()), new File(path), false)) {
      Collection<String> paths = myData.get(type);
      if (paths == null) {
        myData.put(type, paths = ContainerUtilRt.newHashSet());
      }
      paths.add(ExternalSystemApiUtil.toCanonicalPath(path));
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
    for (Map.Entry<ExternalSystemSourceType, Collection<String>> entry : myData.entrySet()) {
      buffer.append(entry.getKey().toString().toLowerCase()).append("=").append(entry.getValue()).append("|");
    }
    if (!myData.isEmpty()) {
      buffer.setLength(buffer.length() - 1);
    }
    return buffer.toString();
  }
}
