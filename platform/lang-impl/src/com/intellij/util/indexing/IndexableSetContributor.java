package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public abstract class IndexableSetContributor implements IndexedRootsProvider {
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

  public static Set<VirtualFile> getRootsToIndex(IndexedRootsProvider provider) {
    return getRootsToIndex(provider, null);
  }

  public static Set<VirtualFile> getRootsToIndex(IndexedRootsProvider provider, @Nullable Project project) {
    if (provider instanceof IndexableSetContributor) {
      return ((IndexableSetContributor)provider).getAdditionalRootsToIndex(project);
    }

    final HashSet<VirtualFile> result = new HashSet<VirtualFile>();
    for (String url : provider.getRootsToIndex()) {
      ContainerUtil.addIfNotNull(VirtualFileManager.getInstance().findFileByUrl(url), result);
    }

    return result;
  }

  public Set<VirtualFile> getAdditionalRootsToIndex(@Nullable Project project) {
    return getAdditionalRootsToIndex();
  }

  public abstract Set<VirtualFile> getAdditionalRootsToIndex();
}
