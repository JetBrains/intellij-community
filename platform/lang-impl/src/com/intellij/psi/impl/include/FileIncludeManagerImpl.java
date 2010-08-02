/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.psi.impl.include;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;

import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class FileIncludeManagerImpl extends FileIncludeManager {

  private final Project myProject;
  private final PsiManager myPsiManager;
  private final PsiFileFactory myPsiFileFactory;
  private final Map<String, FileIncludeProvider> myProviderMap;
  private final CachedValuesManager myCachedValuesManager;

  private final IncludeCacheHolder myIncludedHolder = new IncludeCacheHolder("compile time includes", "runtime includes") {
    @Override
    protected VirtualFile[] computeFiles(final PsiFile file, final boolean compileTimeOnly) {
      final ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
      processIncludes(file, new Processor<FileIncludeInfo>() {
        @Override
        public boolean process(FileIncludeInfo info) {
          if (compileTimeOnly != info.runtimeOnly) {
            PsiFileSystemItem virtualFile = resolveFileInclude(info, file);
            if (virtualFile != null) {
              files.add(virtualFile.getVirtualFile());
            }
          }
          return true;
        }

      });
      return files.toArray(new VirtualFile[files.size()]);
    }
  };

  public void processIncludes(PsiFile file, Processor<FileIncludeInfo> processor) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    List<FileIncludeInfoImpl> infoList = FileIncludeIndex.getIncludes(file.getVirtualFile(), scope);
    for (FileIncludeInfoImpl info : infoList) {
      if (!processor.process(info)) {
        return;
      }
    }
  }

  private final IncludeCacheHolder myIncludingHolder = new IncludeCacheHolder("compile time contexts", "runtime contexts") {
    @Override
    protected VirtualFile[] computeFiles(PsiFile context, boolean compileTimeOnly) {
      final ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
      processIncludingFiles(context, new Processor<Pair<VirtualFile, FileIncludeInfo>>() {
        @Override
        public boolean process(Pair<VirtualFile, FileIncludeInfo> virtualFileFileIncludeInfoPair) {
          files.add(virtualFileFileIncludeInfoPair.first);
          return true;
        }
      });
      return VfsUtil.toVirtualFileArray(files);
    }
  };

  public void processIncludingFiles(PsiFile context, Processor<Pair<VirtualFile, FileIncludeInfo>> processor) {
    context = context.getOriginalFile();
    VirtualFile contextFile = context.getVirtualFile();
    if (contextFile == null) return;
    MultiMap<VirtualFile,FileIncludeInfoImpl> infoList = FileIncludeIndex.getIncludingFileCandidates(context.getName(), GlobalSearchScope.allScope(myProject));
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

  public FileIncludeManagerImpl(Project project, PsiManager psiManager, PsiFileFactory psiFileFactory,
                                CachedValuesManager cachedValuesManager) {
    myProject = project;
    myPsiManager = psiManager;
    myPsiFileFactory = psiFileFactory;

    FileIncludeProvider[] providers = Extensions.getExtensions(FileIncludeProvider.EP_NAME);
    myProviderMap = new HashMap<String, FileIncludeProvider>(providers.length);
    for (FileIncludeProvider provider : providers) {
      FileIncludeProvider old = myProviderMap.put(provider.getId(), provider);
      assert old == null;
    }
    myCachedValuesManager = cachedValuesManager;
  }

  @Override
  public VirtualFile[] getIncludedFiles(VirtualFile file, boolean compileTimeOnly) {
    if (file instanceof VirtualFileWithId) {
      return myIncludedHolder.getAllFiles(file, compileTimeOnly);
    }
    else {
      return VirtualFile.EMPTY_ARRAY;
    }
  }

  @Override
  public VirtualFile[] getIncludingFiles(VirtualFile file, boolean compileTimeOnly) {
    return myIncludingHolder.getAllFiles(file, compileTimeOnly);
  }

  @Override
  public PsiFileSystemItem resolveFileInclude(FileIncludeInfo info, PsiFile context) {

    PsiFileImpl psiFile = (PsiFileImpl)myPsiFileFactory.createFileFromText("dummy.txt", info.path);
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
      protected VirtualFile[] computeFiles(PsiFile file, boolean compileTimeOnly) {
        return IncludeCacheHolder.this.computeFiles(file, compileTimeOnly);
      }
    };

    private final ParameterizedCachedValueProvider<VirtualFile[], PsiFile> RUNTIME_PROVIDER = new IncludedFilesProvider(false) {
      @Override
      protected VirtualFile[] computeFiles(PsiFile file, boolean compileTimeOnly) {
        return IncludeCacheHolder.this.computeFiles(file, compileTimeOnly);
      }
    };

    private IncludeCacheHolder(String compileTimeKey, String runtimeKey) {
      COMPILE_TIME_KEY = Key.create(compileTimeKey);
      RUNTIME_KEY = Key.create(runtimeKey);
    }

    public VirtualFile[] getAllFiles(VirtualFile file, boolean compileTimeOnly) {
      Set<VirtualFile> result = new HashSet<VirtualFile>();
      getFilesRecursively(file, compileTimeOnly, result);
      return result.toArray(new VirtualFile[result.size()]);
    }

    private void getFilesRecursively(VirtualFile file, boolean compileTimeOnly, Set<VirtualFile> result) {
      if (result.contains(file)) return;
      PsiFile psiFile = myPsiManager.findFile(file);
      if (psiFile == null) return;
      VirtualFile[] includes = compileTimeOnly
                               ? myCachedValuesManager.getParameterizedCachedValue(psiFile, COMPILE_TIME_KEY, COMPILE_TIME_PROVIDER, false, psiFile)
                               : myCachedValuesManager.getParameterizedCachedValue(psiFile, RUNTIME_KEY, RUNTIME_PROVIDER, false, psiFile);
      if (includes.length != 0) {
        result.addAll(Arrays.asList(includes));
        for (VirtualFile include : includes) {
          getFilesRecursively(include, compileTimeOnly, result);
        }
      }
    }

    protected abstract VirtualFile[] computeFiles(PsiFile file, boolean compileTimeOnly);

  }

  private abstract static class IncludedFilesProvider implements ParameterizedCachedValueProvider<VirtualFile[], PsiFile> {
    private final boolean myRuntimeOnly;

    public IncludedFilesProvider(boolean runtimeOnly) {
      myRuntimeOnly = runtimeOnly;
    }

    protected abstract VirtualFile[] computeFiles(PsiFile file, boolean compileTimeOnly);

    public CachedValueProvider.Result<VirtualFile[]> compute(PsiFile param) {
      VirtualFile[] value = computeFiles(param, myRuntimeOnly);
      // todo: we need "url modification tracker" for VirtualFile 
      return CachedValueProvider.Result.create(value, ArrayUtil.append(value, param.getVirtualFile()));
    }
  }
}
