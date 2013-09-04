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

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.concurrency.Job;
import com.intellij.concurrency.JobLauncher;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.DocumentWindowImpl;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author cdr
 */
public class InjectedLanguageManagerImpl extends InjectedLanguageManager implements Disposable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl");
  private final Project myProject;
  private final DumbService myDumbService;
  private final AtomicReference<MultiHostInjector> myPsiManagerRegisteredInjectorsAdapter = new AtomicReference<MultiHostInjector>();
  private volatile DaemonProgressIndicator myProgress;

  public static InjectedLanguageManagerImpl getInstanceImpl(Project project) {
    return (InjectedLanguageManagerImpl)InjectedLanguageManager.getInstance(project);
  }

  public InjectedLanguageManagerImpl(Project project, DumbService dumbService) {
    myProject = project;
    myDumbService = dumbService;

    final ExtensionPoint<MultiHostInjector> multiPoint = Extensions.getArea(project).getExtensionPoint(MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME);
    multiPoint.addExtensionPointListener(new ExtensionPointListener<MultiHostInjector>() {
      @Override
      public void extensionAdded(@NotNull MultiHostInjector injector, @Nullable PluginDescriptor pluginDescriptor) {
        registerMultiHostInjector(injector);
      }

      @Override
      public void extensionRemoved(@NotNull MultiHostInjector injector, @Nullable PluginDescriptor pluginDescriptor) {
        unregisterMultiHostInjector(injector);
      }
    },this);
    final ExtensionPointListener<LanguageInjector> myListener = new ExtensionPointListener<LanguageInjector>() {
      @Override
      public void extensionAdded(@NotNull LanguageInjector extension, @Nullable PluginDescriptor pluginDescriptor) {
        psiManagerInjectorsChanged();
      }

      @Override
      public void extensionRemoved(@NotNull LanguageInjector extension, @Nullable PluginDescriptor pluginDescriptor) {
        psiManagerInjectorsChanged();
      }
    };
    final ExtensionPoint<LanguageInjector> psiManagerPoint = Extensions.getRootArea().getExtensionPoint(LanguageInjector.EXTENSION_POINT_NAME);
    psiManagerPoint.addExtensionPointListener(myListener,this);
    myProgress = new DaemonProgressIndicator();
    project.getMessageBus().connect(this).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListenerAdapter() {
      @Override
      public void daemonCancelEventOccurred() {
        myProgress.cancel();
      }
    });
  }

  @Override
  public void dispose() {
  }

  @Override
  public void startRunInjectors(@NotNull final Document hostDocument, final boolean synchronously) {
    if (myProject.isDisposed()) return;
    if (!synchronously && ApplicationManager.getApplication().isWriteAccessAllowed()) return;
    // use cached to avoid recreate PSI in alien project
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    final PsiFile hostPsiFile = documentManager.getCachedPsiFile(hostDocument);
    if (hostPsiFile == null) return;

    final CopyOnWriteArrayList<DocumentWindow> injected =
      (CopyOnWriteArrayList<DocumentWindow>)InjectedLanguageUtil.getCachedInjectedDocuments(hostPsiFile);
    if (injected.isEmpty()) return;

    if (myProgress.isCanceled()) {
      myProgress = new DaemonProgressIndicator();
    }

    final Processor<DocumentWindow> commitProcessor = new Processor<DocumentWindow>() {
      @Override
      public boolean process(DocumentWindow documentWindow) {
        if (myProject.isDisposed()) return false;
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null && indicator.isCanceled()) return false;
        if (documentManager.isUncommited(hostDocument) || !hostPsiFile.isValid()) return false; // will be committed later

        Segment[] ranges = documentWindow.getHostRanges();
        Segment rangeMarker = ranges.length > 0 ? ranges[0] : null;
        PsiElement element = rangeMarker == null ? null : hostPsiFile.findElementAt(rangeMarker.getStartOffset());
        if (element == null) {
          synchronized (PsiLock.LOCK) {
            injected.remove(documentWindow);
          }
          return true;
        }
        final DocumentWindow[] stillInjectedDocument = {null};
        // it is here where the reparse happens and old file contents replaced
        InjectedLanguageUtil.enumerate(element, hostPsiFile, true, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
          @Override
          public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
            stillInjectedDocument[0] = (DocumentWindow)injectedPsi.getViewProvider().getDocument();
            PsiDocumentManagerBase.checkConsistency(injectedPsi, stillInjectedDocument[0]);
          }
        });
        synchronized (PsiLock.LOCK) {
          if (stillInjectedDocument[0] == null) {
            injected.remove(documentWindow);
          }
          else if (stillInjectedDocument[0] != documentWindow) {
            injected.remove(documentWindow);
            injected.addIfAbsent(stillInjectedDocument[0]);
          }
        }

        return true;
      }
    };
    final Runnable commitInjectionsRunnable = new Runnable() {
      @Override
      public void run() {
        if (myProgress.isCanceled()) return;
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<DocumentWindow>(injected), myProgress, true, commitProcessor);
      }
    };

    if (synchronously) {
      if (Thread.holdsLock(PsiLock.LOCK)) {
        // hack for the case when docCommit was called from within PSI modification, e.g. in formatter.
        // we can't spawn threads to do injections there, otherwise a deadlock is imminent
        ContainerUtil.process(new ArrayList<DocumentWindow>(injected), commitProcessor);
      }
      else {
        commitInjectionsRunnable.run();
      }
    }
    else {
      JobLauncher.getInstance().submitToJobThread(Job.DEFAULT_PRIORITY, new Runnable() {
        @Override
        public void run() {
          ApplicationManagerEx.getApplicationEx().tryRunReadAction(commitInjectionsRunnable);
        }
      });
    }
  }

  public void psiManagerInjectorsChanged() {
    LanguageInjector[] extensions = Extensions.getExtensions(LanguageInjector.EXTENSION_POINT_NAME);
    if (extensions.length == 0) {
      MultiHostInjector prev = myPsiManagerRegisteredInjectorsAdapter.getAndSet(null);
      if (prev != null) {
        unregisterMultiHostInjector(prev);
      }
    }
    else {
      PsiManagerRegisteredInjectorsAdapter adapter = new PsiManagerRegisteredInjectorsAdapter();
      if (myPsiManagerRegisteredInjectorsAdapter.compareAndSet(null, adapter)) {
        registerMultiHostInjector(adapter);
      }
    }
  }

  @Override
  public PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement element) {
    final PsiFile file = element.getContainingFile();
    final VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
    if (virtualFile instanceof VirtualFileWindow) {
      PsiElement host = FileContextUtil.getFileContext(file); // use utility method in case the file's overridden getContext()
      if (host instanceof PsiLanguageInjectionHost) {
        return (PsiLanguageInjectionHost)host;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public TextRange injectedToHost(@NotNull PsiElement injectedContext, @NotNull TextRange injectedTextRange) {
    ProperTextRange.assertProperRange(injectedTextRange);
    PsiFile file = injectedContext.getContainingFile();
    if (file == null) return injectedTextRange;
    Document document = PsiDocumentManager.getInstance(file.getProject()).getCachedDocument(file);
    if (!(document instanceof DocumentWindowImpl)) return injectedTextRange;
    DocumentWindowImpl documentWindow = (DocumentWindowImpl)document;
    return documentWindow.injectedToHost(injectedTextRange);
  }
  @Override
  public int injectedToHost(@NotNull PsiElement element, int offset) {
    PsiFile file = element.getContainingFile();
    if (file == null) return offset;
    Document document = PsiDocumentManager.getInstance(file.getProject()).getCachedDocument(file);
    if (!(document instanceof DocumentWindowImpl)) return offset;
    DocumentWindowImpl documentWindow = (DocumentWindowImpl)document;
    return documentWindow.injectedToHost(offset);
  }

  private final ConcurrentMap<Class, MultiHostInjector[]> injectors = new ConcurrentHashMap<Class, MultiHostInjector[]>();
  private final ClassMapCachingNulls<MultiHostInjector> cachedInjectors = new ClassMapCachingNulls<MultiHostInjector>(injectors, new MultiHostInjector[0]);

  @Override
  public void registerMultiHostInjector(@NotNull MultiHostInjector injector) {
    for (Class<? extends PsiElement> place : injector.elementsToInjectIn()) {
      LOG.assertTrue(place != null, injector);
      while (true) {
        MultiHostInjector[] injectors = this.injectors.get(place);
        if (injectors == null) {
          if (this.injectors.putIfAbsent(place, new MultiHostInjector[]{injector}) == null) break;
        }
        else {
          MultiHostInjector[] newInfos = ArrayUtil.append(injectors, injector);
          if (this.injectors.replace(place, injectors, newInfos)) break;
        }
      }
    }
    cachedInjectors.clearCache();
  }

  @Override
  public boolean unregisterMultiHostInjector(@NotNull MultiHostInjector injector) {
    boolean removed = false;
    Iterator<Map.Entry<Class,MultiHostInjector[]>> iterator = injectors.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Class,MultiHostInjector[]> entry = iterator.next();
      MultiHostInjector[] infos = entry.getValue();
      int i = ArrayUtilRt.find(infos, injector);
      if (i != -1) {
        MultiHostInjector[] newInfos = ArrayUtil.remove(infos, i);
        if (newInfos.length == 0) {
          iterator.remove();
        }
        else {
          injectors.put(entry.getKey(), newInfos);
        }
        removed = true;
      }
    }
    cachedInjectors.clearCache();
    return removed;
  }


  @Override
  public String
  getUnescapedText(@NotNull final PsiElement injectedNode) {
    final StringBuilder text = new StringBuilder(injectedNode.getTextLength());
    // gather text from (patched) leaves
    injectedNode.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        String leafText = InjectedLanguageUtil.getUnescapedLeafText(element, false);
        if (leafText != null) {
          text.append(leafText);
          return;
        }
        super.visitElement(element);
      }
    });
    return text.toString();
  }

  /**
   *  intersection may spread over several injected fragments
   *  @param rangeToEdit range in encoded(raw) PSI
   *  @return list of ranges in encoded (raw) PSI
   */
  @Override
  @SuppressWarnings({"ConstantConditions", "unchecked"})
  @NotNull
  public List<TextRange> intersectWithAllEditableFragments(@NotNull PsiFile injectedPsi, @NotNull TextRange rangeToEdit) {
    Place shreds = InjectedLanguageUtil.getShreds(injectedPsi);
    if (shreds == null) return Collections.emptyList();
    Object result = null; // optimization: TextRange or ArrayList
    int count = 0;
    int offset = 0;
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      TextRange encodedRange = TextRange.from(offset + shred.getPrefix().length(), shred.getRangeInsideHost().getLength());
      TextRange intersection = encodedRange.intersection(rangeToEdit);
      if (intersection != null) {
        count++;
        if (count == 1) {
          result = intersection;
        }
        else if (count == 2) {
          TextRange range = (TextRange)result;
          if (range.isEmpty()) {
            result = intersection;
            count = 1;
          }
          else if (intersection.isEmpty()) {
            count = 1;
          }
          else {
            List<TextRange> list = new ArrayList<TextRange>();
            list.add(range);
            list.add(intersection);
            result = list;
          }
        }
        else if (intersection.isEmpty()) {
          count--;
        }
        else {
          ((List<TextRange>)result).add(intersection);
        }
      }
      offset += shred.getPrefix().length() + shred.getRangeInsideHost().getLength() + shred.getSuffix().length();
    }
    return count == 0 ? Collections.<TextRange>emptyList() : count == 1 ? Collections.singletonList((TextRange)result) : (List<TextRange>)result;
  }

  @Override
  public boolean isInjectedFragment(@NotNull final PsiFile file) {
    return file.getViewProvider() instanceof InjectedFileViewProvider;
  }

  @Override
  public PsiElement findInjectedElementAt(@NotNull PsiFile hostFile, int hostDocumentOffset) {
    return InjectedLanguageUtil.findInjectedElementNoCommit(hostFile, hostDocumentOffset);
  }

  @Override
  public void dropFileCaches(@NotNull PsiFile file) {
    InjectedLanguageUtil.clearCachedInjectedFragmentsForFile(file);
  }

  @Override
  public PsiFile getTopLevelFile(@NotNull PsiElement element) {
    return InjectedLanguageUtil.getTopLevelFile(element);
  }

  @NotNull
  @Override
  public List<DocumentWindow> getCachedInjectedDocuments(@NotNull PsiFile hostPsiFile) {
    return InjectedLanguageUtil.getCachedInjectedDocuments(hostPsiFile);
  }

  @Override
  public void enumerate(@NotNull PsiElement host, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {
    InjectedLanguageUtil.enumerate(host, visitor);
  }

  @Override
  public void enumerateEx(@NotNull PsiElement host,
                          @NotNull PsiFile containingFile,
                          boolean probeUp,
                          @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {
    InjectedLanguageUtil.enumerate(host, containingFile, probeUp, visitor);
  }

  private final Map<Class,MultiHostInjector[]> myInjectorsClone = new HashMap<Class, MultiHostInjector[]>();
  @TestOnly
  public static void pushInjectors(@NotNull Project project) {
    InjectedLanguageManagerImpl cachedManager = (InjectedLanguageManagerImpl)project.getUserData(INSTANCE_CACHE);
    if (cachedManager == null) return;
    try {
      assert cachedManager.myInjectorsClone.isEmpty() : cachedManager.myInjectorsClone;
    }
    finally {
      cachedManager.myInjectorsClone.clear();
    }
    cachedManager.myInjectorsClone.putAll(cachedManager.injectors);
  }
  @TestOnly
  public static void checkInjectorsAreDisposed(@NotNull Project project) {
    InjectedLanguageManagerImpl cachedManager = (InjectedLanguageManagerImpl)project.getUserData(INSTANCE_CACHE);
    if (cachedManager == null) {
      return;
    }
    try {
      for (Map.Entry<Class, MultiHostInjector[]> entry : cachedManager.injectors.entrySet()) {
        Class key = entry.getKey();
        if (cachedManager.myInjectorsClone.isEmpty()) return;
        MultiHostInjector[] oldInjectors = cachedManager.myInjectorsClone.get(key);
        for (MultiHostInjector injector : entry.getValue()) {
          if (!ArrayUtil.contains(injector, oldInjectors)) {
            throw new AssertionError("Injector was not disposed: " + key + " -> " + injector);
          }
        }
      }
    }
    finally {
      cachedManager.myInjectorsClone.clear();
    }
  }

  public interface InjProcessor {
    boolean process(PsiElement element, MultiHostInjector injector);
  }
  public void processInPlaceInjectorsFor(@NotNull PsiElement element, @NotNull InjProcessor processor) {
    MultiHostInjector[] infos = cachedInjectors.get(element.getClass());
    if (infos != null) {
      final boolean dumb = myDumbService.isDumb();
      for (MultiHostInjector injector : infos) {
        if (dumb && !DumbService.isDumbAware(injector)) {
          continue;
        }

        if (!processor.process(element, injector)) return;
      }
    }
  }

  @Override
  @Nullable
  public List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@NotNull final PsiElement host) {
    if (!(host instanceof PsiLanguageInjectionHost) || !((PsiLanguageInjectionHost) host).isValidHost()) {
      return null;
    }
    final PsiElement inTree = InjectedLanguageUtil.loadTree(host, host.getContainingFile());
    final List<Pair<PsiElement, TextRange>> result = new SmartList<Pair<PsiElement, TextRange>>();
    InjectedLanguageUtil.enumerate(inTree, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      @Override
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        for (PsiLanguageInjectionHost.Shred place : places) {
          if (place.getHost() == inTree) {
            result.add(new Pair<PsiElement, TextRange>(injectedPsi, place.getRangeInsideHost()));
          }
        }
      }
    });
    return result.isEmpty() ? null : result;
  }

  private static class PsiManagerRegisteredInjectorsAdapter implements MultiHostInjector {
    @Override
    public void getLanguagesToInject(@NotNull final MultiHostRegistrar injectionPlacesRegistrar, @NotNull PsiElement context) {
      final PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)context;
      InjectedLanguagePlaces placesRegistrar = new InjectedLanguagePlaces() {
        @Override
        public void addPlace(@NotNull Language language, @NotNull TextRange rangeInsideHost, @NonNls @Nullable String prefix, @NonNls @Nullable String suffix) {
          ProperTextRange.assertProperRange(rangeInsideHost);
          injectionPlacesRegistrar
            .startInjecting(language)
            .addPlace(prefix, suffix, host, rangeInsideHost)
            .doneInjecting();
        }
      };
      for (LanguageInjector injector : Extensions.getExtensions(LanguageInjector.EXTENSION_POINT_NAME)) {
        injector.getLanguagesToInject(host, placesRegistrar);
      }
    }

    @Override
    @NotNull
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
      return Arrays.asList(PsiLanguageInjectionHost.class);
    }
  }
}
