// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.include;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
@ApiStatus.Internal
public final class FileIncludeManagerImpl extends FileIncludeManager implements Disposable {
  private final Project myProject;
  private final PsiManager myPsiManager;
  private final PsiFileFactory myPsiFileFactory;
  // We save PsiFiles involved in includes here. Otherwise, we'd have to reparse/recompute includes in plugin.xmls on each typing because
  // - include graph is cached in the PsiFile userdata
  // - these PsiFiles are subjects to gc because FileManagerImpl stores FileViewProviders by weak references, and nobody retains plugin.xml's PsiFile
  private final Set<Reference<PsiFile>> cache = ConcurrentCollectionFactory.createConcurrentSet();
  private final IncludeCacheHolder myIncludedHolder = new IncludeCacheHolder("compile time includes", "runtime includes") {
    @Override
    protected VirtualFile[] computeFiles(final PsiFile file, final boolean compileTimeOnly) {
      final Set<VirtualFile> files = new HashSet<>();
      processIncludes(file, info -> {
        if (compileTimeOnly != info.runtimeOnly) {
          PsiFileSystemItem item = resolveFileInclude(info, file);
          if (item != null) {
            ContainerUtil.addIfNotNull(files, item.getVirtualFile());
          }
        }
        return true;
      });
      return VfsUtilCore.toVirtualFileArray(files);
    }
  };

  public void processIncludes(PsiFile file, Processor<? super FileIncludeInfo> processor) {
    List<FileIncludeInfo> infoList = FileIncludeIndex.getIncludes(file.getVirtualFile(), myProject);
    for (FileIncludeInfo info : infoList) {
      if (!processor.process(info)) {
        return;
      }
    }
  }

  private final IncludeCacheHolder myIncludingHolder = new IncludeCacheHolder("compile time contexts", "runtime contexts") {
    @Override
    protected VirtualFile[] computeFiles(PsiFile context, boolean compileTimeOnly) {
      final Set<VirtualFile> files = new HashSet<>();
      processIncludingFiles(context, virtualFileFileIncludeInfoPair -> {
        files.add(virtualFileFileIncludeInfoPair.first);
        return true;
      });
      return VfsUtilCore.toVirtualFileArray(files);
    }
  };

  @Override
  public void processIncludingFiles(PsiFile context, Processor<? super Pair<VirtualFile, FileIncludeInfo>> processor) {
    context = context.getOriginalFile();
    VirtualFile contextFile = context.getVirtualFile();
    if (contextFile == null) return;
    if (FileBasedIndex.getInstance().getFileBeingCurrentlyIndexed() != null) return;

    String originalName = context.getName();
    Collection<String> names = getPossibleIncludeNames(context, originalName);

    GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    for (String name : names) {
      MultiMap<VirtualFile,FileIncludeInfoImpl> infoList = FileIncludeIndex.getIncludingFileCandidates(name, scope);
      for (VirtualFile candidate : infoList.keySet()) {
        PsiFile psiFile = myPsiManager.findFile(candidate);
        if (psiFile == null || context.equals(psiFile)) continue;
        for (FileIncludeInfo info : infoList.get(candidate)) {
          PsiFileSystemItem item = resolveFileInclude(info, psiFile);
          if (item != null && contextFile.equals(item.getVirtualFile())) {
            if (!processor.process(Pair.create(candidate, info))) {
              return;
            }
          }
        }
      }
    }
  }

  private static @NotNull Collection<String> getPossibleIncludeNames(@NotNull PsiFile context, @NotNull String originalName) {
    Collection<String> names = new HashSet<>();
    names.add(originalName);
    for (FileIncludeProvider provider : FileIncludeProvider.EP_NAME.getExtensionList()) {
      String newName = provider.getIncludeName(context, originalName);
      if (!Strings.areSameInstance(newName, originalName)) {
        names.add(newName);
      }
    }
    return names;
  }

  private final Map<String, FileIncludeProvider> myProviderMap = new HashMap<>();

  public FileIncludeManagerImpl(@NotNull Project project) {
    myProject = project;
    myPsiManager = PsiManager.getInstance(project);
    myPsiFileFactory = PsiFileFactory.getInstance(myProject);

    FileIncludeProvider.EP_NAME.getPoint().addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull FileIncludeProvider provider, @NotNull PluginDescriptor pluginDescriptor) {
        FileIncludeProvider old = myProviderMap.put(provider.getId(), provider);
        assert old == null;
      }

      @Override
      public void extensionRemoved(@NotNull FileIncludeProvider provider, @NotNull PluginDescriptor pluginDescriptor) {
        myProviderMap.remove(provider.getId());
      }
    }, true, this);
  }

  @Override
  public void dispose() {
  }

  @Override
  public VirtualFile[] getIncludedFiles(@NotNull VirtualFile file, boolean compileTimeOnly) {
    return getIncludedFiles(file, compileTimeOnly, false);
  }

  @Override
  public VirtualFile[] getIncludedFiles(@NotNull VirtualFile file, boolean compileTimeOnly, boolean recursively) {
    if (file instanceof VirtualFileWithId) {
      return myIncludedHolder.getAllFiles(file, compileTimeOnly, recursively);
    }
    else {
      return VirtualFile.EMPTY_ARRAY;
    }
  }

  @Override
  public VirtualFile[] getIncludingFiles(@NotNull VirtualFile file, boolean compileTimeOnly) {
    return myIncludingHolder.getAllFiles(file, compileTimeOnly, false);
  }

  @Override
  public PsiFileSystemItem resolveFileInclude(final @NotNull FileIncludeInfo info, final @NotNull PsiFile context) {
    return doResolve(info, context);
  }

  private @Nullable PsiFileSystemItem doResolve(final @NotNull FileIncludeInfo info, final @NotNull PsiFile context) {
    if (info instanceof FileIncludeInfoImpl) {
      String id = ((FileIncludeInfoImpl)info).providerId;
      FileIncludeProvider provider = id == null ? null : myProviderMap.get(id);
      final PsiFileSystemItem resolvedByProvider = provider == null ? null : provider.resolveIncludedFile(info, context);
      if (resolvedByProvider != null) {
        return resolvedByProvider;
      }
    }

    PsiFileImpl psiFile = (PsiFileImpl)myPsiFileFactory.createFileFromText("dummy.txt", FileTypes.PLAIN_TEXT, info.path);
    psiFile.setOriginalFile(context);
    return new FileReferenceSet(psiFile) {
      @Override
      protected boolean useIncludingFileAsContext() {
        return false;
      }
    }.resolve();
  }

  private abstract class IncludeCacheHolder {

    private final Key<ParameterizedCachedValue<VirtualFile[], PsiFile>> COMPILE_TIME_KEY;
    private final Key<ParameterizedCachedValue<VirtualFile[], PsiFile>> RUNTIME_KEY;

    private final ParameterizedCachedValueProvider<VirtualFile[], PsiFile> COMPILE_TIME_PROVIDER = new IncludedFilesProvider(true) {
      @Override
      protected @NotNull VirtualFile @NotNull [] computeFiles(@NotNull PsiFile file, boolean compileTimeOnly) {
        return IncludeCacheHolder.this.computeFiles(file, compileTimeOnly);
      }
    };

    private final ParameterizedCachedValueProvider<VirtualFile[], PsiFile> RUNTIME_PROVIDER = new IncludedFilesProvider(false) {
      @Override
      protected @NotNull VirtualFile @NotNull [] computeFiles(@NotNull PsiFile file, boolean compileTimeOnly) {
        return IncludeCacheHolder.this.computeFiles(file, compileTimeOnly);
      }
    };

    private IncludeCacheHolder(String compileTimeKey, String runtimeKey) {
      COMPILE_TIME_KEY = Key.create(compileTimeKey);
      RUNTIME_KEY = Key.create(runtimeKey);
    }

    private VirtualFile @NotNull [] getAllFiles(@NotNull VirtualFile file, boolean compileTimeOnly, boolean recursively) {
      if (recursively) {
        Set<VirtualFile> result = new HashSet<>();
        getAllFilesRecursively(file, compileTimeOnly, result);
        return VfsUtilCore.toVirtualFileArray(result);
      }
      return getFiles(file, compileTimeOnly);
    }

    private void getAllFilesRecursively(@NotNull VirtualFile file, boolean compileTimeOnly, Set<? super VirtualFile> result) {
      if (!result.add(file)) return;
      VirtualFile[] includes = getFiles(file, compileTimeOnly);
      for (VirtualFile include : includes) {
        getAllFilesRecursively(include, compileTimeOnly, result);
      }
    }

    private VirtualFile[] getFiles(@NotNull VirtualFile file, boolean compileTimeOnly) {
      PsiFile psiFile = myPsiManager.findFile(file);
      if (psiFile == null) {
        return VirtualFile.EMPTY_ARRAY;
      }
      if (compileTimeOnly) {
        return CachedValuesManager.getManager(myProject)
          .getParameterizedCachedValue(psiFile, COMPILE_TIME_KEY, COMPILE_TIME_PROVIDER, false, psiFile);
      }
      return CachedValuesManager.getManager(myProject).getParameterizedCachedValue(psiFile, RUNTIME_KEY, RUNTIME_PROVIDER, false, psiFile);
    }

    protected abstract VirtualFile[] computeFiles(PsiFile file, boolean compileTimeOnly);

  }

  private abstract class IncludedFilesProvider implements ParameterizedCachedValueProvider<VirtualFile[], PsiFile> {
    private final boolean myRuntimeOnly;

    IncludedFilesProvider(boolean runtimeOnly) {
      myRuntimeOnly = runtimeOnly;
    }

    protected abstract @NotNull VirtualFile @NotNull [] computeFiles(@NotNull PsiFile file, boolean compileTimeOnly);

    @Override
    public CachedValueProvider.Result<VirtualFile[]> compute(@NotNull PsiFile psiFile) {
      VirtualFile[] value = computeFiles(psiFile, myRuntimeOnly);
      // todo: we need "url modification tracker" for VirtualFile
      List<Object> deps = new ArrayList<>(value.length +1);
      for (VirtualFile file : value) {
        PsiFile depPsiFile = psiFile.getManager().findFile(file);
        if (depPsiFile != null) {
          cache.add(new SoftReference<>(depPsiFile));
        }
        Document document = FileDocumentManager.getInstance().getCachedDocument(file);
        Object dep = document == null ? file : document;
        deps.add(dep);
      }
      // do not add PsiFile as dependency because it will be translated to PSI_MOD_COUNT which fires too often, even for unrelated files
      Document document = psiFile.getViewProvider().getDocument();
      if (document != null) {
        deps.add(document);
      }
      cache.add(new SoftReference<>(psiFile));
      if (deps.isEmpty()) {
        deps.add(ProjectRootManager.getInstance(myProject));
      }
      return CachedValueProvider.Result.create(value, List.copyOf(deps));
    }
  }
}
