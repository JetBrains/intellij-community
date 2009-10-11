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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    protected VirtualFile[] computeFiles(VirtualFile file, boolean compileTimeOnly) {
      GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
      List<FileIncludeInfoImpl> infoList = FileIncludeIndex.getIncludes(file, scope);
      ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
      for (FileIncludeInfoImpl info : infoList) {
        if (compileTimeOnly && info.runtimeOnly) {
          continue;
        }
        FileIncludeProvider includeProvider = myProviderMap.get(info.providerId);
        if (includeProvider != null) {
          VirtualFile virtualFile = includeProvider.resolveInclude(info, file, myProject);
          if (virtualFile != null) {
            files.add(virtualFile);
          }
        }
      }
      return files.toArray(new VirtualFile[files.size()]);
    }
  };

  private final IncludeCacheHolder myIncludingHolder = new IncludeCacheHolder("compile time contexts", "runtime contexts") {
    @Override
    protected VirtualFile[] computeFiles(VirtualFile file, boolean compileTimeOnly) {
      MultiMap<VirtualFile,FileIncludeInfoImpl> infoList = FileIncludeIndex.getIncludingFileCandidates(file.getName(), GlobalSearchScope.allScope(myProject));
      ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
      for (VirtualFile candidate : infoList.keySet()) {
        for (FileIncludeInfoImpl info : infoList.get(candidate)) {
          FileIncludeProvider includeProvider = myProviderMap.get(info.providerId);
          if (includeProvider != null) {
            if (file.equals(includeProvider.resolveInclude(info, candidate, myProject))) {
              files.add(candidate);
            }
          }
        }
      }
      return files.toArray(new VirtualFile[files.size()]);
    }
  };

  public FileIncludeManagerImpl(Project project, PsiManager psiManager, PsiFileFactory psiFileFactory) {
    myProject = project;
    myPsiManager = psiManager;
    myPsiFileFactory = psiFileFactory;

    FileIncludeProvider[] providers = Extensions.getExtensions(FileIncludeProvider.EP_NAME);
    myProviderMap = new HashMap<String, FileIncludeProvider>(providers.length);
    for (FileIncludeProvider provider : providers) {
      FileIncludeProvider old = myProviderMap.put(provider.getId(), provider);
      assert old == null;
    }
    myCachedValuesManager = myPsiManager.getCachedValuesManager();
  }

  @Override
  public VirtualFile[] getIncludedFiles(VirtualFile file, boolean compileTimeOnly) {
    return myIncludedHolder.getAllFiles(file, compileTimeOnly);
  }


  @Override
  public VirtualFile[] getIncludingFiles(VirtualFile file, boolean compileTimeOnly) {
    return myIncludingHolder.getAllFiles(file, compileTimeOnly);
  }

  @Override
  public PsiFileSystemItem resolveFileReference(String text, VirtualFile context) {
        
    PsiFile originalFile = myPsiManager.findFile(context);
    if (originalFile == null) {
      return null;
    }

    PsiFileImpl psiFile = (PsiFileImpl)myPsiFileFactory.createFileFromText("dummy.txt", text);
    psiFile.setOriginalFile(originalFile);

    return new FileReferenceSet(psiFile).resolve();
  }

  private abstract class IncludeCacheHolder {

    private final Key<ParameterizedCachedValue<VirtualFile[], VirtualFile>> COMPILE_TIME_KEY;
    private final Key<ParameterizedCachedValue<VirtualFile[], VirtualFile>> RUNTIME_KEY;

    private final ParameterizedCachedValueProvider<VirtualFile[], VirtualFile> COMPILE_TIME_PROVIDER = new IncludedFilesProvider(false) {
      @Override
      protected VirtualFile[] computeFiles(VirtualFile file, boolean compileTimeOnly) {
        return IncludeCacheHolder.this.computeFiles(file, compileTimeOnly);
      }
    };

    private final ParameterizedCachedValueProvider<VirtualFile[], VirtualFile> RUNTIME_PROVIDER = new IncludedFilesProvider(true) {
      @Override
      protected VirtualFile[] computeFiles(VirtualFile file, boolean compileTimeOnly) {
        return IncludeCacheHolder.this.computeFiles(file, compileTimeOnly);
      }
    };

    private IncludeCacheHolder(String compileTimeKey, String runtimeKey) {
      COMPILE_TIME_KEY = Key.create(compileTimeKey);
      RUNTIME_KEY = Key.create(runtimeKey);
    }

    public VirtualFile[] getAllFiles(VirtualFile file, boolean compileTimeOnly) {
      ArrayList<VirtualFile[]> result = new ArrayList<VirtualFile[]>();
      getFilesRecursively(file, compileTimeOnly, result);
      switch (result.size()) {
        case 0:
          return VirtualFile.EMPTY_ARRAY;
        case 1:
          return result.get(0);
        default:
          int size = 0;
          for (VirtualFile[] files : result) {
            size+=files.length;
          }
          VirtualFile[] files = new VirtualFile[size];
          int pos = 0;
          for (VirtualFile[] virtualFiles : result) {
            System.arraycopy(virtualFiles, 0, files, pos, virtualFiles.length);
            pos += virtualFiles.length;
          }
          return files;
      }
    }

    private void getFilesRecursively(VirtualFile file, boolean compileTimeOnly, List<VirtualFile[]> result) {
      VirtualFile[] includes = compileTimeOnly
                               ? myCachedValuesManager.getParameterizedCachedValue(file, COMPILE_TIME_KEY, COMPILE_TIME_PROVIDER, false, file)
                               : myCachedValuesManager.getParameterizedCachedValue(file, RUNTIME_KEY, RUNTIME_PROVIDER, false, file);
      if (includes.length != 0) {
        result.add(includes);
        for (VirtualFile include : includes) {
          getFilesRecursively(include, compileTimeOnly, result);
        }
      }
    }

    protected abstract VirtualFile[] computeFiles(VirtualFile file, boolean compileTimeOnly);

  }

  private abstract class IncludedFilesProvider implements ParameterizedCachedValueProvider<VirtualFile[], VirtualFile> {
    private boolean myRuntimeOnly;

    public IncludedFilesProvider(boolean runtimeOnly) {
      myRuntimeOnly = runtimeOnly;
    }

    protected abstract VirtualFile[] computeFiles(VirtualFile file, boolean compileTimeOnly);

    public CachedValueProvider.Result<VirtualFile[]> compute(VirtualFile param) {
      VirtualFile[] value = computeFiles(param, myRuntimeOnly);
      // todo: we need "url modification tracker" for VirtualFile 
      return CachedValueProvider.Result.create(value, ArrayUtil.append(value, param));
    }
  }
}
