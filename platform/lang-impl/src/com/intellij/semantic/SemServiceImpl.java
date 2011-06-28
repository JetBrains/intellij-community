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
package com.intellij.semantic;

import com.intellij.ProjectTopics;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.tree.LazyParseableElement;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.StripedLockConcurrentHashMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.pico.IdeaPicoContainer;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author peter
 */
@SuppressWarnings({"unchecked"})
public class SemServiceImpl extends SemService{
  private static final Comparator<SemKey> KEY_COMPARATOR = new Comparator<SemKey>() {
    public int compare(SemKey o1, SemKey o2) {
      return o2.getUniqueId() - o1.getUniqueId();
    }
  };
  private final ConcurrentWeakHashMap<PsiElement, SoftReference<FileChunk>> myCache = new ConcurrentWeakHashMap<PsiElement, SoftReference<FileChunk>>();
  private final MultiMap<SemKey, NullableFunction<PsiElement, ? extends SemElement>> myProducers;
  private final MultiMap<SemKey, SemKey> myInheritors;
  private final Project myProject;

  private boolean myBulkChange = false;

  public SemServiceImpl(Project project, PsiManager psiManager) {
    myProject = project;
    final MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(ProjectTopics.MODIFICATION_TRACKER, new PsiModificationTracker.Listener() {
      public void modificationCountChanged() {
        if (!isInsideAtomicChange()) {
          clearCache();
        }
      }
    });

    ((PsiManagerEx)psiManager).registerRunnableToRunOnChange(new Runnable() {
      public void run() {
        if (!isInsideAtomicChange()) {
          clearCache();
        }
      }
    });


    myProducers = collectProducers();

    myInheritors = cacheKeyHierarchy(myProducers.keySet());
    
    final LowMemoryWatcher watcher = LowMemoryWatcher.register(new LowMemoryWatcher.ForceableAdapter() {
      public void force() {
        clearCache();
        //System.out.println("SemService cache flushed");
      }
    });
    ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerAdapter() {
      public void projectClosing(Project project) {
        watcher.stop();
      }
    });
  }

  private static MultiMap<SemKey, SemKey> cacheKeyHierarchy(Collection<SemKey> allKeys) {
    final MultiMap<SemKey, SemKey> result = new MultiMap<SemKey, SemKey>();
    ContainerUtil.process(allKeys, new Processor<SemKey>() {
      public boolean process(SemKey key) {
        result.putValue(key, key);
        for (SemKey parent : key.getSupers()) {
          result.putValue(parent, key);
          process(parent);
        }
        return true;
      }
    });
    for (final SemKey each : result.keySet()) {
      final List<SemKey> inheritors = new ArrayList<SemKey>(new HashSet<SemKey>(result.get(each)));
      Collections.sort(inheritors, KEY_COMPARATOR);
      result.put(each, inheritors);
    }
    return result;
  }

  private MultiMap<SemKey, NullableFunction<PsiElement, ? extends SemElement>> collectProducers() {
    final MultiMap<SemKey, NullableFunction<PsiElement, ? extends SemElement>> map = new MultiMap<SemKey, NullableFunction<PsiElement, ? extends SemElement>>();

    final SemRegistrar registrar = new SemRegistrar() {
      public <T extends SemElement, V extends PsiElement> void registerSemElementProvider(SemKey<T> key,
                                                                                          final ElementPattern<? extends V> place,
                                                                                          final NullableFunction<V, T> provider) {
        map.putValue(key, new NullableFunction<PsiElement, SemElement>() {
          public SemElement fun(PsiElement element) {
            if (place.accepts(element)) {
              return provider.fun((V)element);
            }
            return null;
          }
        });
      }
    };

    final IdeaPicoContainer container = new IdeaPicoContainer(myProject.getPicoContainer());
    container.registerComponentInstance(SemService.class.getName(), this);
    for (SemContributorEP contributor : myProject.getExtensions(SemContributor.EP_NAME)) {
      contributor.registerSemProviders(container, registrar);
    }

    return map;
  }

  public void clearCache() {
    for (PsiElement element : myCache.keySet()) {
      final FileChunk chunk = obtainChunk(element);
      if (chunk != null) {
        chunk.unhinge();
      }
      myCache.remove(element);
    }
  }

  @Override
  public void performAtomicChange(@NotNull Runnable change) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    final boolean oldValue = myBulkChange;
    myBulkChange = true;
    try {
      change.run();
    }
    finally {
      myBulkChange = oldValue;
      if (!oldValue) {
        clearCache();
      }
    }
  }

  @Override
  public boolean isInsideAtomicChange() {
    return myBulkChange;
  }

  @Nullable
  public <T extends SemElement> List<T> getSemElements(final SemKey<T> key, @NotNull final PsiElement psi) {
    final PsiElement root = getRootElement(psi);
    if (root == null) {
      return Collections.emptyList();
    }

    List<T> cached = _getCachedSemElements(key, true, psi, root);
    if (cached != null) {
      return cached;
    }

    RecursionGuard.StackStamp stamp = RecursionManager.createGuard("semService").markStack();

    LinkedHashSet<T> result = new LinkedHashSet<T>();
    final Map<SemKey, List<SemElement>> map = new THashMap<SemKey, List<SemElement>>();
    for (final SemKey each : myInheritors.get(key)) {
      List<SemElement> list = createSemElements(each, psi);
      map.put(each, list);
      result.addAll((List<T>)list);
    }

    if (stamp.mayCacheNow()) {
      final ConcurrentMap<SemKey, List<SemElement>> persistent = cacheOrGetMap(psi, root);
      for (SemKey semKey : map.keySet()) {
        persistent.putIfAbsent(semKey, map.get(semKey));
      }
    }

    return new ArrayList<T>(result);
  }

  @Nullable
  private static PsiElement getRootElement(@NotNull PsiElement psi) {
    if (psi instanceof PsiDirectory || psi instanceof PsiDirectoryContainer) {
      return psi;
    }
    return psi.getContainingFile();
  }

  @NotNull 
  private List<SemElement> createSemElements(SemKey key, PsiElement psi) {
    List<SemElement> result = null;
    final Collection<NullableFunction<PsiElement, ? extends SemElement>> producers = myProducers.get(key);
    if (!producers.isEmpty()) {
      for (final NullableFunction<PsiElement, ? extends SemElement> producer : producers) {
        final SemElement element = producer.fun(psi);
        if (element != null) {
          if (result == null) result = new SmartList<SemElement>();
          result.add(element);
        }  
      }
    }
    return result == null ? Collections.<SemElement>emptyList() : Collections.unmodifiableList(result);
  }

  @Nullable
  public <T extends SemElement> List<T> getCachedSemElements(SemKey<T> key, @NotNull PsiElement psi) {
    return _getCachedSemElements(key, false, psi, getRootElement(psi));
  }

  @Nullable
  private <T extends SemElement> List<T> _getCachedSemElements(SemKey<T> key, boolean paranoid, final PsiElement element,
                                                               @Nullable PsiElement root) {
    final FileChunk chunk = obtainChunk(root);
    if (chunk == null) return null;

    final ConcurrentMap<SemKey, List<SemElement>> map = chunk.map.get(element);
    if (map == null) return null;

    List<T> singleList = null;
    LinkedHashSet<T> result = null;
    final List<SemKey> inheritors = (List<SemKey>)myInheritors.get(key);
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < inheritors.size(); i++) {
      List<T> cached = (List<T>)map.get(inheritors.get(i));

      if (cached == null && paranoid) {
        return null;
      }

      if (cached != null && cached != Collections.<T>emptyList()) {
        if (singleList == null) {
          singleList = cached;
          continue;
        }

        if (result == null) {
          result = new LinkedHashSet<T>(singleList);
        }
        result.addAll(cached);
      }
    }


    if (result == null) {
      if (singleList != null) {
        return singleList;
      }

      return Collections.emptyList();
    }

    return new ArrayList<T>(result);
  }

  @Nullable
  private FileChunk obtainChunk(@Nullable PsiElement root) {
    final SoftReference<FileChunk> ref = myCache.get(root);
    return ref == null ? null : ref.get();
  }

  public <T extends SemElement> void setCachedSemElement(SemKey<T> key, @NotNull PsiElement psi, @Nullable T semElement) {
    final PsiElement rootElement = getRootElement(psi);
    if (rootElement != null) {
      cacheOrGetMap(psi, rootElement).put(key, ContainerUtil.<SemElement>createMaybeSingletonList(semElement));
    }
  }

  @Override
  public void clearCachedSemElements(@NotNull PsiElement psi) {
    final FileChunk chunk = obtainChunk(getRootElement(psi));
    if (chunk != null) {
      chunk.map.remove(psi);
    }
  }

  private ConcurrentMap<SemKey, List<SemElement>> cacheOrGetMap(final PsiElement element, @NotNull PsiElement root) {
    FileChunk chunk = obtainChunk(root);
    if (chunk == null) {
      chunk = new FileChunk(root);
      myCache.putIfAbsent(root, new SoftReference(chunk));
    }

    ConcurrentMap<SemKey, List<SemElement>> map = chunk.map.get(element);
    if (map == null) {
      map = ConcurrencyUtil.cacheOrGet(chunk.map, element, new StripedLockConcurrentHashMap<SemKey, List<SemElement>>());
    }
    return map;
  }

  private static class FileChunk {
    private static final Key<FileChunk> SEM_SERVICE_CHUNK = Key.create("semServiceChunkHardReference");
    final ConcurrentMap<PsiElement, ConcurrentMap<SemKey, List<SemElement>>> map = new StripedLockConcurrentHashMap<PsiElement, ConcurrentMap<SemKey, List<SemElement>>>();
    @Nullable final PsiElement anchor;

    FileChunk(PsiElement root) {
      if (root instanceof PsiFile) {
        if (!(root instanceof PsiFileEx) || ((PsiFileEx)root).isContentsLoaded()) {
          final ASTNode node = root.getNode();
          if (node instanceof LazyParseableElement && ((LazyParseableElement)node).isParsed()) {
            final PsiElement child = root.getFirstChild();
            if (child != null) {
              anchor = child;
              child.putUserData(SEM_SERVICE_CHUNK, this);
              return;
            }
          }
        }
      }
      anchor = null;
    }

    void unhinge() {
      if (anchor != null) {
        anchor.putUserData(SEM_SERVICE_CHUNK, null);
      }
    }
  }

}
