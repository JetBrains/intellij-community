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

import com.intellij.injected.editor.DocumentWindowImpl;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author cdr
 */
public class InjectedLanguageManagerImpl extends InjectedLanguageManager {
  private final Project myProject;
  private final DumbService myDumbService;
  private final AtomicReference<MultiHostInjector> myPsiManagerRegisteredInjectorsAdapter = new AtomicReference<MultiHostInjector>();

  public static InjectedLanguageManagerImpl getInstanceImpl(Project project) {
    return (InjectedLanguageManagerImpl)InjectedLanguageManager.getInstance(project);
  }

  public InjectedLanguageManagerImpl(Project project, DumbService dumbService) {
    myProject = project;
    myDumbService = dumbService;

    final ExtensionPoint<MultiHostInjector> multiPoint = Extensions.getArea(project).getExtensionPoint(MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME);
    multiPoint.addExtensionPointListener(new ExtensionPointListener<MultiHostInjector>() {
      public void extensionAdded(MultiHostInjector injector, @Nullable PluginDescriptor pluginDescriptor) {
        registerMultiHostInjector(injector);
      }

      public void extensionRemoved(MultiHostInjector injector, @Nullable PluginDescriptor pluginDescriptor) {
        unregisterMultiHostInjector(injector);
      }
    });
    final ExtensionPointListener<LanguageInjector> myListener = new ExtensionPointListener<LanguageInjector>() {
      public void extensionAdded(LanguageInjector extension, @Nullable PluginDescriptor pluginDescriptor) {
        psiManagerInjectorsChanged();
      }

      public void extensionRemoved(LanguageInjector extension, @Nullable PluginDescriptor pluginDescriptor) {
        psiManagerInjectorsChanged();
      }
    };
    final ExtensionPoint<LanguageInjector> psiManagerPoint = Extensions.getRootArea().getExtensionPoint(LanguageInjector.EXTENSION_POINT_NAME);
    psiManagerPoint.addExtensionPointListener(myListener);
    Disposer.register(project, new Disposable() {
      public void dispose() {
        psiManagerPoint.removeExtensionPointListener(myListener);
      }
    });
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

  @NotNull
  public TextRange injectedToHost(@NotNull PsiElement injectedContext, @NotNull TextRange injectedTextRange) {
    ProperTextRange.assertProperRange(injectedTextRange);
    PsiFile file = injectedContext.getContainingFile();
    if (file == null) return injectedTextRange;
    Document document = PsiDocumentManager.getInstance(injectedContext.getProject()).getCachedDocument(file);
    if (!(document instanceof DocumentWindowImpl)) return injectedTextRange;
    DocumentWindowImpl documentWindow = (DocumentWindowImpl)document;
    return documentWindow.injectedToHost(injectedTextRange);
  }
  public int injectedToHost(@NotNull PsiElement element, int offset) {
    PsiFile file = element.getContainingFile();
    if (file == null) return offset;
    Document document = PsiDocumentManager.getInstance(element.getProject()).getCachedDocument(file);
    if (!(document instanceof DocumentWindowImpl)) return offset;
    DocumentWindowImpl documentWindow = (DocumentWindowImpl)document;
    return documentWindow.injectedToHost(offset);
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
    final StringBuilder text = new StringBuilder(injectedNode.getTextLength());
    // gather text from (patched) leaves
    injectedNode.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        String unescaped = element.getUserData(UNESCAPED_TEXT);
        if (unescaped != null) {
          text.append(unescaped);
          return;
        }
        if (element.getFirstChild() == null) {
          text.append(element.getText());
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
  @SuppressWarnings({"ConstantConditions"})
  @NotNull
  public List<TextRange> intersectWithAllEditableFragments(@NotNull PsiFile injectedPsi, @NotNull TextRange rangeToEdit) {
    Place shreds = InjectedLanguageUtil.getShreds(injectedPsi);
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

  public boolean isInjectedFragment(final PsiFile file) {
    return file.getViewProvider() instanceof InjectedFileViewProvider;
  }

  @Override
  public PsiElement findInjectedElementAt(@NotNull PsiFile hostFile, int hostDocumentOffset) {
    return InjectedLanguageUtil.findInjectedElementNoCommitWithOffset(hostFile, hostDocumentOffset);
  }

  private final Map<Class,MultiHostInjector[]> myInjectorsClone = new HashMap<Class, MultiHostInjector[]>();
  @TestOnly
  public void pushInjectors() {
    try {
      assert myInjectorsClone.isEmpty() : myInjectorsClone;
    }
    finally {
      myInjectorsClone.clear();
    }
    myInjectorsClone.putAll(injectors);
  }
  @TestOnly
  public void checkInjectorsAreDisposed() {
    try {
      for (Map.Entry<Class, MultiHostInjector[]> entry : injectors.entrySet()) {
        Class key = entry.getKey();
        MultiHostInjector[] oldInjectors = myInjectorsClone.get(key);
        for (MultiHostInjector injector : entry.getValue()) {
          if (!ArrayUtil.contains(injector, oldInjectors)) {
            throw new AssertionError("Injector was not disposed: " + key + " -> " + injector);
          }
        }
      }
    }
    finally {
      myInjectorsClone.clear();
    }
  }

  public interface InjProcessor {
    boolean process(PsiElement element, MultiHostInjector injector);
  }
  public void processInPlaceInjectorsFor(@NotNull PsiElement element, @NotNull InjProcessor processor) {
    final boolean dumb = myDumbService.isDumb();
    MultiHostInjector[] infos = cachedInjectors.get(element.getClass());
    if (infos != null) {
      for (MultiHostInjector injector : infos) {
        if (dumb && !DumbService.isDumbAware(injector)) {
          continue;
        }

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
  }

  private static class PsiManagerRegisteredInjectorsAdapter implements MultiHostInjector {
    private final PsiManagerEx myPsiManager;

    private PsiManagerRegisteredInjectorsAdapter(PsiManagerEx psiManager) {
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
}
