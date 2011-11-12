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

package com.intellij.psi.impl.smartPointers;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.UnsafeWeakList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.List;
import java.util.Set;

public class SmartPointerManagerImpl extends SmartPointerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl");

  private static final Key<List<SmartPointerEx>> SMART_POINTERS_IN_PSI_FILE_KEY = Key.create("SMART_POINTERS_IN_PSI_FILE_KEY");
  private static final Key<Boolean> BELTS_ARE_FASTEN_KEY = Key.create("BELTS_ARE_FASTEN_KEY");

  private final Project myProject;

  public SmartPointerManagerImpl(Project project) {
    myProject = project;
  }

  public void fastenBelts(@NotNull PsiFile file, int offset, @Nullable RangeMarker cachedRangeMarker) {
    synchronized (file) {
      if (areBeltsFastened(file)) return;

      file.putUserData(BELTS_ARE_FASTEN_KEY, Boolean.TRUE);

      List<SmartPointerEx> pointers = getPointers(file);
      if (pointers == null) return;
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
      Document document = psiDocumentManager.getDocument(file);
      if (document instanceof DocumentImpl) {
      }

      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < pointers.size(); i++) {
        SmartPointerEx pointer = pointers.get(i);
        if (pointer != null) {
          pointer.fastenBelt(offset, cachedRangeMarker);
        }
      }

      for (DocumentWindow injectedDoc : InjectedLanguageUtil.getCachedInjectedDocuments(file)) {
        PsiFile injectedFile = psiDocumentManager.getPsiFile(injectedDoc);
        if (injectedFile == null) continue;
        RangeMarker cachedMarker = getCachedRangeMarkerToInjectedFragment(injectedFile);
        fastenBelts(injectedFile, 0, cachedMarker);
      }
    }
  }

  private static RangeMarker getCachedRangeMarkerToInjectedFragment(@NotNull PsiFile injectedFile) {
    PsiElement hostContext = injectedFile.getContext();
    RangeMarker cachedMarker = null;
    if (hostContext != null) {
      SmartPsiElementPointer<PsiElement> cachedPointer = getCachedPointer(hostContext);
      SmartPointerElementInfo info = cachedPointer == null ? null : ((SmartPsiElementPointerImpl)cachedPointer).getElementInfo();
      if (info instanceof SelfElementInfo) {
        cachedMarker = ((SelfElementInfo)info).getMarker();
      }
    }
    return cachedMarker;
  }

  public void unfastenBelts(@NotNull PsiFile file, int offset) {
    synchronized (file) {
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
      file.putUserData(BELTS_ARE_FASTEN_KEY, null);

      List<SmartPointerEx> pointers = getPointers(file);
      if (pointers == null) return;

      Document document = psiDocumentManager.getDocument(file);
      if (document instanceof DocumentImpl) {
      }

      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < pointers.size(); i++) {
        SmartPointerEx pointer = pointers.get(i);
        if (pointer != null) {
          pointer.unfastenBelt(offset);
        }
      }

      for (DocumentWindow injectedDoc : InjectedLanguageUtil.getCachedInjectedDocuments(file)) {
        PsiFile injectedFile = psiDocumentManager.getPsiFile(injectedDoc);
        if (injectedFile == null) continue;
        unfastenBelts(injectedFile, 0);
      }
    }
  }

  public static void synchronizePointers(@NotNull PsiFile file) {
    final Set<Language> languages = file.getViewProvider().getLanguages();
    for (Language language : languages) {
      final PsiFile f = file.getViewProvider().getPsi(language);
      synchronized (f) {
        _synchronizePointers(f);
      }
    }
  }

  private static void _synchronizePointers(@NotNull PsiFile file) {
    List<SmartPointerEx> pointers = getPointers(file);
    if (pointers == null) return;

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < pointers.size(); i++) {
      SmartPointerEx pointer = pointers.get(i);
      if (pointer != null) {
        pointer.documentAndPsiInSync();
      }
    }

    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
    for (Document document : InjectedLanguageUtil.getCachedInjectedDocuments(file)) {
      PsiFile injectedfile = psiDocumentManager.getPsiFile(document);
      if (injectedfile == null) continue;
      _synchronizePointers(injectedfile);
    }
  }

  private static final Key<Reference<SmartPsiElementPointer>> CACHED_SMART_POINTER_KEY = Key.create("CACHED_SMART_POINTER_KEY");
  @Override
  @NotNull
  public <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    PsiFile containingFile = element.getContainingFile();
    return createSmartPsiElementPointer(element, containingFile);
  }
  @Override
  @NotNull
  public <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element, PsiFile containingFile) {
    if (containingFile != null && !containingFile.isValid() || containingFile == null && !element.isValid()) {
      LOG.error("Invalid element:" + element);
    }
    SmartPsiElementPointer<E> cachedPointer = getCachedPointer(element);
    if (cachedPointer != null) {
      return cachedPointer;
    }

    SmartPointerEx<E> pointer = new SmartPsiElementPointerImpl<E>(myProject, element, containingFile);
    initPointer(pointer, containingFile);
    element.putUserData(CACHED_SMART_POINTER_KEY, new SoftReference<SmartPsiElementPointer>(pointer));
    return pointer;
  }

  private static <E extends PsiElement> SmartPsiElementPointer<E> getCachedPointer(@NotNull E element) {
    Reference<SmartPsiElementPointer> data = element.getUserData(CACHED_SMART_POINTER_KEY);
    return data == null ? null : data.get();
  }

  @Override
  @NotNull
  public SmartPsiFileRange createSmartPsiFileRangePointer(@NotNull PsiFile file, @NotNull TextRange range) {
    if (!file.isValid()) {
      LOG.error("Invalid element:" + file);
    }
    SmartPsiFileRangePointerImpl pointer = new SmartPsiFileRangePointerImpl(file, ProperTextRange.create(range));
    initPointer(pointer, file);

    return pointer;
  }

  private static <E extends PsiElement> void initPointer(@NotNull SmartPointerEx<E> pointer, PsiFile containingFile) {
    if (containingFile == null) return;
    synchronized (containingFile) {
      List<SmartPointerEx> pointers = getPointers(containingFile);
      if (pointers == null) {
        pointers = new UnsafeWeakList<SmartPointerEx>(); // we synchronise access anyway by containingFile
        containingFile.putUserData(SMART_POINTERS_IN_PSI_FILE_KEY, pointers);
      }
      pointers.add(pointer);

      if (areBeltsFastened(containingFile)) {
        pointer.fastenBelt(0, null);
      }
    }
  }

  private static List<SmartPointerEx> getPointers(@NotNull PsiFile containingFile) {
    return containingFile.getUserData(SMART_POINTERS_IN_PSI_FILE_KEY);
  }

  private static boolean areBeltsFastened(@NotNull PsiFile file) {
    return file.getUserData(BELTS_ARE_FASTEN_KEY) == Boolean.TRUE;
  }


  @Override
  @NotNull
  @Deprecated
  public <E extends PsiElement> SmartPsiElementPointer<E> createLazyPointer(@NotNull E element) {
    return createSmartPsiElementPointer(element);
  }

  @Override
  public boolean pointToTheSameElement(@NotNull SmartPsiElementPointer pointer1, @NotNull SmartPsiElementPointer pointer2) {
    return SmartPsiElementPointerImpl.pointsToTheSameElementAs(pointer1, pointer2);
  }
}
