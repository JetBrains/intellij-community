package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.*;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author cdr
 */
public class InjectedLanguageManagerImpl extends InjectedLanguageManager {
  private final ConcurrentMap<VirtualFile, List<VirtualFileWindow>> cachedFiles = new ConcurrentWeakHashMap<VirtualFile, List<VirtualFileWindow>>();
  private final Project myProject;
  private final PsiManagerEx myPsiManager;
  private final AtomicReference<MultiHostInjector> myPsiManagerRegisteredInjectorsAdapter = new AtomicReference<MultiHostInjector>();
  private final ExtensionPointListener<LanguageInjector> myListener;

  public static InjectedLanguageManagerImpl getInstanceImpl(Project project) {
    return (InjectedLanguageManagerImpl)InjectedLanguageManager.getInstance(project);
  }

  public InjectedLanguageManagerImpl(Project project, PsiManagerEx psiManager) {
    myProject = project;
    myPsiManager = psiManager;

    final ExtensionPoint<MultiHostInjector> multiPoint = Extensions.getArea(project).getExtensionPoint(MULTIHOST_INJECTOR_EP_NAME);
    multiPoint.addExtensionPointListener(new ExtensionPointListener<MultiHostInjector>() {
      public void extensionAdded(MultiHostInjector injector, @Nullable PluginDescriptor pluginDescriptor) {
        registerMultiHostInjector(injector);
      }

      public void extensionRemoved(MultiHostInjector injector, @Nullable PluginDescriptor pluginDescriptor) {
        unregisterMultiHostInjector(injector);
      }
    });
    myListener = new ExtensionPointListener<LanguageInjector>() {
      public void extensionAdded(LanguageInjector extension, @Nullable PluginDescriptor pluginDescriptor) {
        psiManagerInjectorsChanged();
      }

      public void extensionRemoved(LanguageInjector extension, @Nullable PluginDescriptor pluginDescriptor) {
        psiManagerInjectorsChanged();
      }
    };
    ExtensionPoint<LanguageInjector> psiManagerPoint = Extensions.getRootArea().getExtensionPoint(LanguageInjector.EXTENSION_POINT_NAME);
    psiManagerPoint.addExtensionPointListener(myListener);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void psiManagerInjectorsChanged() {
    PsiManagerEx psiManager = (PsiManagerEx)PsiManager.getInstance(myProject);
    List<? extends LanguageInjector> injectors = psiManager.getLanguageInjectors();
    LanguageInjector[] extensions = Extensions.getExtensions(LanguageInjector.EXTENSION_POINT_NAME);
    if (injectors.isEmpty() && extensions.length == 0) {
      MultiHostInjector prev = myPsiManagerRegisteredInjectorsAdapter.getAndSet(null);
      if (prev != null) {
        unregisterMultiHostInjector(prev);
      }
    }
    else {
      PsiManagerRegisteredInjectorsAdapter adapter = new PsiManagerRegisteredInjectorsAdapter(psiManager);
      if (myPsiManagerRegisteredInjectorsAdapter.compareAndSet(null, adapter)) {
        registerMultiHostInjector(adapter);
      }
    }
  }

  VirtualFileWindow createVirtualFile(final Language language,
                                      final VirtualFile hostVirtualFile,
                                      final DocumentWindowImpl documentWindow,
                                      StringBuilder text) {
    VirtualFileWindow virtualFile = new VirtualFileWindowImpl(hostVirtualFile, documentWindow, language, text);
    List<VirtualFileWindow> cachedList = cachedFiles.get(hostVirtualFile);
    if (cachedList == null) {
      cachedList = ConcurrencyUtil.cacheOrGet(cachedFiles, hostVirtualFile, new ArrayList<VirtualFileWindow>());
    }
    return insertFileNear(cachedList, virtualFile);
  }

  private static final MyRangesIntersectionComparator rangesIntersectionComparator = new MyRangesIntersectionComparator();
  private VirtualFileWindow insertFileNear(List<VirtualFileWindow> cachedList, VirtualFileWindow virtualFile) {
    FileManager fileManager = myPsiManager.getFileManager();
    synchronized (cachedList) {
      int insertionIndex = Collections.binarySearch(cachedList, virtualFile, rangesIntersectionComparator);
      if (insertionIndex < 0) insertionIndex = -insertionIndex - 1;

      //check for validity all files adjacent to our file
      while (insertionIndex != 0 && insertionIndex < cachedList.size()) {
        if (compareRanges(cachedList.get(insertionIndex), virtualFile) == 0) {
          insertionIndex--;
        }
        else {
          break;
        }
      }
      // remove all invalid files immediately before ours
      while (insertionIndex > 0) {
        if (checkAndRemoveInvalidFile(insertionIndex-1,cachedList,fileManager) == null) {
          insertionIndex--;
        }
        else {
          break;
        }
      }

      // and after ours
      boolean enoughChecking = false;
      int i = insertionIndex;
      while (i < cachedList.size()) {
        VirtualFileWindow oldValidFile = checkAndRemoveInvalidFile(i,cachedList,fileManager);
        if (oldValidFile == null) {
          enoughChecking = false;
        }
        else {
          if (enoughChecking && compareRanges(oldValidFile, virtualFile) == 1) break;
          i++;
          enoughChecking = true;
        }
      }
      cachedList.add(insertionIndex, virtualFile);
    }
    return virtualFile;
  }
  //returns null if file is invalid and has been removed
  private static VirtualFileWindow checkAndRemoveInvalidFile(int index, List<VirtualFileWindow> cachedList, FileManager fileManager) {
    VirtualFileWindow oldFile = cachedList.get(index);
    boolean isValid = oldFile.isValid();
    if (isValid) {
      PsiFile cached = fileManager.getCachedPsiFile((VirtualFile)oldFile);
      PsiElement context;
      if (cached == null || (context = cached.getContext()) == null || !cached.isValid() || !context.isValid()) {
        isValid = false;
      }
      else if (index < cachedList.size() - 1) {
        VirtualFileWindow nextFile = cachedList.get(index + 1);
        if (nextFile.isValid() && oldFile.getDocumentWindow().areRangesEqual(nextFile.getDocumentWindow())) {
          PsiFile nextCached = fileManager.getCachedPsiFile((VirtualFile)nextFile);
          if (nextCached != null && nextCached.isValid()) {
            isValid = false;
          }
        }
      }
    }
    if (isValid) {
      return oldFile;
    }
    cachedList.remove(index);
    fileManager.setViewProvider((VirtualFile)oldFile, null);
    return null;
  }

  public PsiLanguageInjectionHost getInjectionHost(@NotNull PsiElement element) {
    final PsiFile file = element.getContainingFile();
    final VirtualFile virtualFile = file == null ? null : file.getVirtualFile();
    if (virtualFile instanceof VirtualFileWindow) {
      PsiElement host = file.getContext();
      if (host != null) {
        return (PsiLanguageInjectionHost)host;
      }
    }
    return null;
  }

  public TextRange injectedToHost(@NotNull PsiElement element, @NotNull TextRange textRange) {
    ProperTextRange.assertProperRange(textRange);
    PsiFile file = element.getContainingFile();
    if (file == null) return textRange;
    Document document = PsiDocumentManager.getInstance(element.getProject()).getCachedDocument(file);
    if (!(document instanceof DocumentWindowImpl)) return textRange;
    DocumentWindowImpl documentWindow = (DocumentWindowImpl)document;
    return documentWindow.injectedToHost(textRange);
  }

  private final ConcurrentMap<Class, MultiHostInjector[]> injectors = new ConcurrentHashMap<Class, MultiHostInjector[]>();
  private final ClassMapCachingNulls<MultiHostInjector> cachedInjectors = new ClassMapCachingNulls<MultiHostInjector>(injectors, new MultiHostInjector[0]);

  public void registerMultiHostInjector(@NotNull MultiHostInjector injector) {
    for (Class<? extends PsiElement> place : injector.elementsToInjectIn()) {
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

  public boolean unregisterMultiHostInjector(@NotNull MultiHostInjector injector) {
    boolean removed = false;
    Iterator<Map.Entry<Class,MultiHostInjector[]>> iterator = injectors.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Class,MultiHostInjector[]> entry = iterator.next();
      MultiHostInjector[] infos = entry.getValue();
      int i = ArrayUtil.find(infos, injector);
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


  static final Key<String> UNESCAPED_TEXT = Key.create("INJECTED_UNESCAPED_TEXT");
  public String getUnescapedText(@NotNull final PsiElement injectedNode) {
    String text = injectedNode.getUserData(UNESCAPED_TEXT);
    if (text != null) return text;
    return injectedNode.getText();
  }

  /**
   *  intersection may spread over several injected fragments
   *  @param rangeToEdit range in encoded(raw) PSI
   *  @return list of ranges in encoded (raw) PSI
   */
  @NotNull
  public List<TextRange> intersectWithAllEditableFragments(@NotNull PsiFile injectedPsi, @NotNull TextRange rangeToEdit) {
    List<PsiLanguageInjectionHost.Shred> shreds = InjectedLanguageUtil.getShreds(injectedPsi);
    if (shreds == null) return Collections.emptyList();
    Object result = null; // optimization: TextRange or ArrayList
    int count = 0;
    int offset = 0;
    for (PsiLanguageInjectionHost.Shred shred : shreds) {
      TextRange encodedRange = TextRange.from(offset + shred.prefix.length(), shred.getRangeInsideHost().getLength());
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
      offset += shred.prefix.length() + shred.getRangeInsideHost().getLength() + shred.suffix.length();
    }
    return count == 0 ? Collections.<TextRange>emptyList() : count == 1 ? Collections.singletonList((TextRange)result) : (List<TextRange>)result;
  }

  public static interface InjProcessor {
    boolean process(PsiElement element, MultiHostInjector injector);
  }
  public void processInPlaceInjectorsFor(@NotNull PsiElement element, @NotNull InjProcessor processor) {
    MultiHostInjector[] infos = cachedInjectors.get(element.getClass());
    if (infos != null) {
      for (MultiHostInjector injector : infos) {
        if (!processor.process(element, injector)) return;
      }
    }
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "InjectedLanguageManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    ExtensionPoint<LanguageInjector> psiManagerPoint = Extensions.getRootArea().getExtensionPoint(LanguageInjector.EXTENSION_POINT_NAME);
    psiManagerPoint.removeExtensionPointListener(myListener);
  }

  public void clearCaches(VirtualFileWindow virtualFile) {
    VirtualFile hostFile = virtualFile.getDelegate();
    List<VirtualFileWindow> cachedList = cachedFiles.get(hostFile);
    if (cachedList != null) {
      synchronized (cachedList) {
        cachedList.remove(virtualFile);
      }
    }
  }

  private static class PsiManagerRegisteredInjectorsAdapter implements MultiHostInjector {
    private final PsiManagerEx myPsiManager;

    public PsiManagerRegisteredInjectorsAdapter(PsiManagerEx psiManager) {
      myPsiManager = psiManager;
    }

    public void getLanguagesToInject(@NotNull final MultiHostRegistrar injectionPlacesRegistrar, @NotNull PsiElement context) {
      final PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)context;
      InjectedLanguagePlaces placesRegistrar = new InjectedLanguagePlaces() {
        public void addPlace(@NotNull Language language, @NotNull TextRange rangeInsideHost, @NonNls @Nullable String prefix, @NonNls @Nullable String suffix) {
          ProperTextRange.assertProperRange(rangeInsideHost);
          injectionPlacesRegistrar
            .startInjecting(language)
            .addPlace(prefix, suffix, host, rangeInsideHost)
            .doneInjecting();
        }
      };
      for (LanguageInjector injector : myPsiManager.getLanguageInjectors()) {
        injector.getLanguagesToInject(host, placesRegistrar);
      }
      for (LanguageInjector injector : Extensions.getExtensions(LanguageInjector.EXTENSION_POINT_NAME)) {
        injector.getLanguagesToInject(host, placesRegistrar);
      }
    }

    @NotNull
    public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
      return Arrays.asList(PsiLanguageInjectionHost.class);
    }
  }

  private static class MyRangesIntersectionComparator implements Comparator<VirtualFileWindow> {
    public int compare(VirtualFileWindow v1, VirtualFileWindow v2) {
      return compareRanges(v1, v2);
    }

  }

  private static int compareRanges(VirtualFileWindow v1, VirtualFileWindow v2) {
    DocumentWindow d1 = v1.getDocumentWindow();
    DocumentWindow d2 = v2.getDocumentWindow();
    RangeMarker[] ranges1 = d1.getHostRanges();
    RangeMarker[] ranges2 = d2.getHostRanges();
    if (ranges1.length == 0 || ranges2.length == 0) return -1;
    if (ranges1[ranges1.length - 1].getEndOffset() <= ranges2[0].getStartOffset()) return -1;
    if (ranges1[0].getStartOffset() >= ranges2[ranges2.length - 1].getEndOffset()) return 1;
    return 0;
  }
}
