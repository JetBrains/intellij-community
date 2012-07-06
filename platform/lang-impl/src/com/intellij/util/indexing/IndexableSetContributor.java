package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public abstract class IndexableSetContributor implements IndexedRootsProvider {
  
  protected static final Set<VirtualFile> EMPTY_FILE_SET = Collections.unmodifiableSet(new HashSet<VirtualFile>());
  
  @Override
  public final Set<String> getRootsToIndex() {
    return ContainerUtil.map2Set(getAdditionalRootsToIndex(), new NotNullFunction<VirtualFile, String>() {
      @NotNull
      @Override
      public String fun(VirtualFile virtualFile) {
        return virtualFile.getUrl();
      }
    });
  }

  @NotNull
  public static Set<VirtualFile> getProjectRootsToIndex(IndexedRootsProvider provider, Project project) {
    if (provider instanceof IndexableSetContributor) {
      return ((IndexableSetContributor)provider).getAdditionalProjectRootsToIndex(project);
    }
    return EMPTY_FILE_SET;
  }

  public static Set<VirtualFile> getRootsToIndex(IndexedRootsProvider provider) {
    if (provider instanceof IndexableSetContributor) {
      return ((IndexableSetContributor)provider).getAdditionalRootsToIndex();
    }

    final HashSet<VirtualFile> result = new HashSet<VirtualFile>();
    for (String url : provider.getRootsToIndex()) {
      ContainerUtil.addIfNotNull(VirtualFileManager.getInstance().findFileByUrl(url), result);
    }

    return result;
  }

  @NotNull
  public Set<VirtualFile> getAdditionalProjectRootsToIndex(@Nullable Project project) {
    return EMPTY_FILE_SET;
  }

  public abstract Set<VirtualFile> getAdditionalRootsToIndex();
}
