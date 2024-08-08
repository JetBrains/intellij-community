// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

@SuppressWarnings("deprecation")
@ApiStatus.Internal
public final class InjectedLanguageManagerImpl extends InjectedLanguageManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(InjectedLanguageManagerImpl.class);
  static final Object ourInjectionPsiLock = ObjectUtils.sentinel("injectionPsiLock");
  public static final Object INJECTION_BACKGROUND_TOOL_ID = ObjectUtils.sentinel("INJECTION_BACKGROUND_ID");
  public static final Object INJECTION_SYNTAX_TOOL_ID = ObjectUtils.sentinel("INJECTION_BACKGROUND_ID");
  private final Project myProject;
  private final DumbService myDumbService;
  private final PsiDocumentManager myDocManager;

  public static InjectedLanguageManagerImpl getInstanceImpl(Project project) {
    return (InjectedLanguageManagerImpl)InjectedLanguageManager.getInstance(project);
  }

  public InjectedLanguageManagerImpl(Project project) {
    myProject = project;
    myDumbService = DumbService.getInstance(myProject);
    myDocManager = PsiDocumentManager.getInstance(project);

    MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME.addChangeListener(project, this::clearInjectorCache, this);
    LanguageInjector.EXTENSION_POINT_NAME.addChangeListener(this::clearInjectorCache, this);

    project.getMessageBus().connect(this).subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        // When a language plugin is unloaded, make sure we don't have references to any injection host PSI from this language
        // in the injector cache
        clearInjectorCache();
      }

      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        clearInjectorCache();
      }
    });
  }

  @Override
  public void dispose() {
    disposeInvalidEditors();
  }

  public static void clearInvalidInjections(@NotNull PsiFile hostFile) {
    List<DocumentWindow> invalid = ContainerUtil.findAll(InjectedLanguageUtilBase.getCachedInjectedDocuments(hostFile), doc -> !doc.isValid());
    for (DocumentWindow window : invalid) {
      InjectedLanguageUtilBase.clearCaches(hostFile.getProject(), window);
    }
  }

  public static void disposeInvalidEditors() {
    InjectedEditorWindowTracker editorWindowTracker = ApplicationManager.getApplication().getServiceIfCreated(InjectedEditorWindowTracker.class);
    if (editorWindowTracker != null) {
      editorWindowTracker.disposeInvalidEditors();
    }
  }

  @Override
  public PsiLanguageInjectionHost getInjectionHost(@NotNull FileViewProvider injectedProvider) {
    //noinspection removal
    if (!(injectedProvider instanceof InjectedFileViewProvider)) {
      return null;
    }
    //noinspection removal
    return ((InjectedFileViewProvider)injectedProvider).getShreds().getHostPointer().getElement();
  }

  @Override
  public PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement injectedElement) {
    PsiFile file = injectedElement.getContainingFile();
    VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
    if (virtualFile instanceof VirtualFileWindow) {
      // use utility method in case the file's overridden getContext()
      PsiElement host = FileContextUtil.getFileContext(file);
      if (host instanceof PsiLanguageInjectionHost) {
        return (PsiLanguageInjectionHost)host;
      }
    }
    return InjectedLanguageUtilBase.findInjectionHost(file);
  }

  @Override
  public @NotNull TextRange injectedToHost(@NotNull PsiElement injectedContext, @NotNull TextRange injectedTextRange) {
    DocumentWindow documentWindow = getDocumentWindow(injectedContext);
    return documentWindow == null ? injectedTextRange : documentWindow.injectedToHost(injectedTextRange);
  }

  @Override
  public int injectedToHost(@NotNull PsiElement element, int offset) {
    DocumentWindow documentWindow = getDocumentWindow(element);
    return documentWindow == null ? offset : documentWindow.injectedToHost(offset);
  }

  @Override
  public int injectedToHost(@NotNull PsiElement injectedContext, int injectedOffset, boolean minHostOffset) {
    DocumentWindow documentWindow = getDocumentWindow(injectedContext);
    return documentWindow == null ? injectedOffset : ((DocumentWindowImpl)documentWindow).injectedToHost(injectedOffset, minHostOffset);
  }

  private static DocumentWindow getDocumentWindow(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) {
      return null;
    }
    Document document = PsiDocumentManager.getInstance(file.getProject()).getCachedDocument(file);
    return document instanceof DocumentWindow ? (DocumentWindow)document : null;
  }

  // used only from tests => no need for complex synchronization
  private final Set<MultiHostInjector> myManualInjectors = Collections.synchronizedSet(new LinkedHashSet<>());
  private volatile ClassMapCachingNulls<MultiHostInjector> cachedInjectors;

  public void processInjectableElements(@NotNull Collection<? extends PsiElement> in, @NotNull Processor<? super PsiElement> processor) {
    ClassMapCachingNulls<MultiHostInjector> map = getInjectorMap();
    for (PsiElement element : in) {
      if (map.get(element.getClass()) != null) {
        processor.process(element);
      }
    }
  }

  private @NotNull ClassMapCachingNulls<MultiHostInjector> getInjectorMap() {
    ClassMapCachingNulls<MultiHostInjector> cached = cachedInjectors;
    if (cached != null) {
      return cached;
    }

    ClassMapCachingNulls<MultiHostInjector> result = calcInjectorMap();
    cachedInjectors = result;
    return result;
  }

  private @NotNull ClassMapCachingNulls<MultiHostInjector> calcInjectorMap() {
    Map<Class<?>, MultiHostInjector[]> injectors = new HashMap<>();

    List<MultiHostInjector> allInjectors = new ArrayList<>(myManualInjectors);
    allInjectors.addAll(MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME.getExtensions(myProject));
    if (LanguageInjector.EXTENSION_POINT_NAME.hasAnyExtensions()) {
      allInjectors.add(PsiManagerRegisteredInjectorsAdapter.INSTANCE);
    }

    for (MultiHostInjector injector : allInjectors) {
      for (Class<? extends PsiElement> place : injector.elementsToInjectIn()) {
        LOG.assertTrue(place != null, injector);
        MultiHostInjector[] existing = injectors.get(place);
        injectors.put(place, existing == null ? new MultiHostInjector[]{injector} : ArrayUtil.append(existing, injector));
      }
    }

    return new ClassMapCachingNulls<>(injectors, new MultiHostInjector[0], allInjectors);
  }

  private void clearInjectorCache() {
    cachedInjectors = null;
    if (myProject.isDisposed() || myProject.isDefault()) {
      return;
    }

    for (VirtualFile file : FileEditorManager.getInstance(myProject).getOpenFiles()) {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      if (psiFile != null) {
        for (DocumentWindow document : InjectedLanguageUtilBase.getCachedInjectedDocuments(psiFile)) {
          InjectedEditorWindowTracker.getInstance().disposeEditorFor(document);
        }
        dropFileCaches(psiFile);
      }
    }
  }

  @Override
  public void registerMultiHostInjector(@NotNull MultiHostInjector injector, @NotNull Disposable parentDisposable) {
    myManualInjectors.add(injector);
    clearInjectorCache();
    Disposer.register(parentDisposable, () -> unregisterMultiHostInjector(injector));
  }

  private void unregisterMultiHostInjector(@NotNull MultiHostInjector injector) {
    try {
      myManualInjectors.remove(injector);
    }
    finally {
      clearInjectorCache();
    }
  }


  @Override
  public @NotNull String getUnescapedText(@NotNull PsiElement injectedNode) {
    String leafText = InjectedLanguageUtilBase.getUnescapedLeafText(injectedNode, false);
    if (leafText != null) {
      return leafText; // optimization
    }
    StringBuilder text = new StringBuilder(injectedNode.getTextLength());
    // gather text from (patched) leaves
    injectedNode.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        String leafText = InjectedLanguageUtilBase.getUnescapedLeafText(element, false);
        if (leafText != null) {
          text.append(leafText);
          return;
        }
        super.visitElement(element);
      }
    });
    return text.toString();
  }

  @Override
  public int mapInjectedOffsetToUnescaped(@NotNull PsiFile injectedFile, int injectedOffset) {
    if (injectedOffset < 0) return injectedOffset;
    var visitor = new PsiRecursiveElementWalkingVisitor() {
      int unescapedOffset = 0;
      int escapedOffset = 0;

      @Override
      public void visitElement(@NotNull PsiElement element) {
        String leafText = InjectedLanguageUtilBase.getUnescapedLeafText(element, false);
        if (leafText != null) {
          unescapedOffset += leafText.length();
          escapedOffset += element.getTextLength();
          if (escapedOffset >= injectedOffset) {
            unescapedOffset -= escapedOffset - injectedOffset;
            stopWalking();
          }
        }
        super.visitElement(element);
      }
    };
    injectedFile.accept(visitor);
    return visitor.unescapedOffset;
  }

  @Override
  public int mapUnescapedOffsetToInjected(@NotNull PsiFile injectedFile, int offset) {
    if (offset < 0) return offset;
    var visitor = new PsiRecursiveElementWalkingVisitor() {
      int unescapedOffset = 0;
      int escapedOffset = 0;

      @Override
      public void visitElement(@NotNull PsiElement element) {
        String leafText = InjectedLanguageUtilBase.getUnescapedLeafText(element, false);
        if (leafText != null) {
          unescapedOffset += leafText.length();
          escapedOffset += element.getTextLength();
          if (unescapedOffset >= offset) {
            escapedOffset -= unescapedOffset - offset;
            stopWalking();
          }
        }
        super.visitElement(element);
      }
    };
    injectedFile.accept(visitor);
    return visitor.escapedOffset;
  }

  /**
   *  intersection may spread over several injected fragments
   *  @param rangeToEdit range in encoded(raw) PSI
   *  @return list of ranges in encoded (raw) PSI
   */
  @Override
  public @NotNull List<TextRange> intersectWithAllEditableFragments(@NotNull PsiFile injectedPsi, @NotNull TextRange rangeToEdit) {
    Place shreds = InjectedLanguageUtilBase.getShreds(injectedPsi);
    if (shreds == null) return Collections.emptyList();
    Object result = null; // optimization: TextRange or ArrayList<TextRange>
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
            List<TextRange> list = new ArrayList<>();
            list.add(range);
            list.add(intersection);
            result = list;
          }
        }
        else if (intersection.isEmpty()) {
          count--;
        }
        else {
          //noinspection unchecked
          ((List<TextRange>)result).add(intersection);
        }
      }
      offset += shred.getPrefix().length() + shred.getRangeInsideHost().getLength() + shred.getSuffix().length();
    }
    //noinspection unchecked,ConstantConditions
    return count == 0 ? Collections.emptyList() : count == 1 ? Collections.singletonList((TextRange)result) : (List<TextRange>)result;
  }

  @Override
  public boolean isInjectedViewProvider(@NotNull FileViewProvider viewProvider) {
    //noinspection removal
    return viewProvider instanceof InjectedFileViewProvider;
  }

  @Override
  public PsiElement findInjectedElementAt(@NotNull PsiFile hostFile, int hostDocumentOffset) {
    return InjectedLanguageUtilBase.findInjectedElementNoCommit(hostFile, hostDocumentOffset);
  }

  @Override
  public void dropFileCaches(@NotNull PsiFile file) {
    InjectedLanguageUtilBase.clearCachedInjectedFragmentsForFile(file);
  }

  @Override
  public PsiFile getTopLevelFile(@NotNull PsiElement element) {
    return InjectedLanguageUtilBase.getTopLevelFile(element);
  }

  @Override
  public @NotNull List<DocumentWindow> getCachedInjectedDocumentsInRange(@NotNull PsiFile hostPsiFile, @NotNull TextRange range) {
    return InjectedLanguageUtilBase.getCachedInjectedDocumentsInRange(hostPsiFile, range);
  }

  @Override
  public void enumerate(@NotNull PsiElement host, @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {
    InjectedLanguageUtilBase.enumerate(host, visitor);
  }

  @Override
  public void enumerateEx(@NotNull PsiElement host,
                          @NotNull PsiFile containingFile,
                          boolean probeUp,
                          @NotNull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {
    InjectedLanguageUtilBase.enumerate(host, containingFile, probeUp, visitor);
  }

  @Override
  public @NotNull List<TextRange> getNonEditableFragments(@NotNull DocumentWindow window) {
    List<TextRange> result = new ArrayList<>();
    int offset = 0;
    for (PsiLanguageInjectionHost.Shred shred : ((DocumentWindowImpl)window).getShreds()) {
      Segment hostRange = shred.getHostRangeMarker();
      if (hostRange == null) continue;

      offset = appendRange(result, offset, shred.getPrefix().length());
      offset += hostRange.getEndOffset() - hostRange.getStartOffset();
      offset = appendRange(result, offset, shred.getSuffix().length());
    }

    return result;
  }

  @Override
  public boolean mightHaveInjectedFragmentAtOffset(@NotNull Document hostDocument, int hostOffset) {
    return InjectedLanguageUtilBase.mightHaveInjectedFragmentAtCaret(myProject, hostDocument, hostOffset);
  }

  @Override
  public @NotNull DocumentWindow freezeWindow(@NotNull DocumentWindow document) {
    Place shreds = ((DocumentWindowImpl)document).getShreds();
    Project project = shreds.getHostPointer().getProject();
    DocumentEx delegate = ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(project)).getLastCommittedDocument(document.getDelegate());
    Place place = new Place();
    place.addAll(ContainerUtil.map(shreds, shred -> ((ShredImpl)shred).withPsiRange()));
    return new DocumentWindowImpl(delegate, place);
  }

  private static int appendRange(@NotNull List<TextRange> result, int start, int length) {
    if (length > 0) {
      int lastIndex = result.size() - 1;
      TextRange lastRange = lastIndex >= 0 ? result.get(lastIndex) : null;
      if (lastRange != null && lastRange.getEndOffset() == start) {
        result.set(lastIndex, lastRange.grown(length));
      }
      else {
        result.add(TextRange.from(start, length));
      }
    }
    return start + length;
  }

  private final Map<Class<?>, MultiHostInjector[]> myInjectorsClone = new HashMap<>();

  @TestOnly
  public static void pushInjectors(@NotNull Project project) {
    InjectedLanguageManagerImpl cachedManager = (InjectedLanguageManagerImpl)project.getServiceIfCreated(InjectedLanguageManager.class);
    if (cachedManager == null) {
      return;
    }

    try {
      assert cachedManager.myInjectorsClone.isEmpty() : cachedManager.myInjectorsClone;
    }
    finally {
      cachedManager.myInjectorsClone.clear();
    }
    cachedManager.myInjectorsClone.putAll(cachedManager.getInjectorMap().getBackingMap());
  }

  @TestOnly
  public static void checkInjectorsAreDisposed(@NotNull Project project) {
    InjectedLanguageManagerImpl cachedManager = (InjectedLanguageManagerImpl)project.getServiceIfCreated(InjectedLanguageManager.class);
    if (cachedManager == null) {
      return;
    }

    try {
      ClassMapCachingNulls<MultiHostInjector> cached = cachedManager.cachedInjectors;
      if (cached == null) {
        return;
      }

      for (Map.Entry<Class<?>, MultiHostInjector[]> entry : cached.getBackingMap().entrySet()) {
        Class<?> key = entry.getKey();
        if (cachedManager.myInjectorsClone.isEmpty()) {
          return;
        }

        MultiHostInjector[] oldInjectors = cachedManager.myInjectorsClone.get(key);
        for (MultiHostInjector injector : entry.getValue()) {
          if (ArrayUtil.indexOf(oldInjectors, injector) == -1) {
            throw new AssertionError("Injector was not disposed: " + key + " -> " + injector);
          }
        }
      }
    }
    finally {
      cachedManager.myInjectorsClone.clear();
    }
  }

  InjectionResult processInPlaceInjectorsFor(@NotNull PsiFile hostPsiFile, @NotNull PsiElement element) {
    MultiHostInjector[] infos = getInjectorMap().get(element.getClass());
    if (infos == null || infos.length == 0) {
      return null;
    }

    if (element instanceof PsiLanguageInjectionHost
        && !((PsiLanguageInjectionHost)element).isValidHost()) {
      return null;
    }

    InjectionRegistrarImpl hostRegistrar = new InjectionRegistrarImpl(myProject, hostPsiFile, element, myDocManager);
    for (MultiHostInjector injector : infos) {
      if (!myDumbService.isUsableInCurrentContext(injector)) {
        continue;
      }

      injector.getLanguagesToInject(hostRegistrar, element);
      InjectionResult result = hostRegistrar.getInjectedResult();
      if (result != null) return result;
    }
    return null;
  }

  @Override
  public @Nullable List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@NotNull PsiElement host) {
    if (!(host instanceof PsiLanguageInjectionHost) || !((PsiLanguageInjectionHost) host).isValidHost()) {
      return null;
    }
    PsiElement inTree = InjectedLanguageUtilBase.loadTree(host, host.getContainingFile());
    List<Pair<PsiElement, TextRange>> result = new SmartList<>();
    enumerate(inTree, (injectedPsi, places) -> {
      for (PsiLanguageInjectionHost.Shred place : places) {
        if (place.getHost() == inTree) {
          result.add(new Pair<>(injectedPsi, place.getRangeInsideHost()));
        }
      }
    });
    return result.isEmpty() ? null : result;
  }

  public static boolean isInjectionRelated(Object toolId) {
    return toolId == INJECTION_BACKGROUND_TOOL_ID || toolId == INJECTION_SYNTAX_TOOL_ID;
  }

  private static class PsiManagerRegisteredInjectorsAdapter implements MultiHostInjector {
    static final PsiManagerRegisteredInjectorsAdapter INSTANCE = new PsiManagerRegisteredInjectorsAdapter();
    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar injectionPlacesRegistrar, @NotNull PsiElement context) {
      PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)context;
      InjectedLanguagePlaces placesRegistrar = (language, rangeInsideHost, prefix, suffix) -> injectionPlacesRegistrar
        .startInjecting(language)
        .addPlace(prefix, suffix, host, rangeInsideHost)
        .doneInjecting();
      for (LanguageInjector injector : LanguageInjector.EXTENSION_POINT_NAME.getExtensionList()) {
        injector.getLanguagesToInject(host, placesRegistrar);
      }
    }

    @Override
    public @NotNull List<Class<? extends PsiElement>> elementsToInjectIn() {
      return Collections.singletonList(PsiLanguageInjectionHost.class);
    }
  }
}
