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

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SmartPointerManagerImpl extends SmartPointerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl");

  private static final Key<List<WeakReference<SmartPointerEx>>> SMART_POINTERS_IN_PSI_FILE_KEY = Key.create("SMART_POINTERS_IN_DOCUMENT_KEY");
  private static final Key<Boolean> BELTS_ARE_FASTEN_KEY = Key.create("BELTS_ARE_FASTEN_KEY");

  private final Project myProject;

  public SmartPointerManagerImpl(Project project) {
    myProject = project;
  }

  public static void fastenBelts(PsiFile file, int offset) {
    synchronized (file) {
      if (areBeltsFastened(file)) return;

      file.putUserData(BELTS_ARE_FASTEN_KEY, Boolean.TRUE);

      List<WeakReference<SmartPointerEx>> pointers = getPointers(file);
      if (pointers == null) return;

      int index = 0;
      for (int i = 0; i < pointers.size(); i++) {
        WeakReference<SmartPointerEx> reference = pointers.get(i);
        SmartPointerEx pointer = reference.get();
        if (pointer != null) {
          pointer.fastenBelt(offset);
          pointers.set(index++, reference);
        }
      }

      final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
      for(Document document:InjectedLanguageUtil.getCachedInjectedDocuments(file)) {
        PsiFile injectedfile = psiDocumentManager.getPsiFile(document);
        if (injectedfile == null) continue;
        fastenBelts(injectedfile, 0);
      }

      int size = pointers.size();
      for (int i = size - 1; i >= index; i--) {
        pointers.remove(i);
      }
    }
  }

  public static void unfastenBelts(PsiFile file, int offset) {
    final Set<Language> languages = file.getViewProvider().getLanguages();
    for (Language language : languages) {
      final PsiFile f = file.getViewProvider().getPsi(language);
      f.putUserData(BELTS_ARE_FASTEN_KEY, null);

      List<WeakReference<SmartPointerEx>> pointers = getPointers(file);
      if (pointers == null) return;

      int index = 0;
      for (int i = 0; i < pointers.size(); i++) {
        WeakReference<SmartPointerEx> reference = pointers.get(i);
        SmartPointerEx pointer = reference.get();
        if (pointer != null) {
          pointer.unfastenBelt(offset);
          pointers.set(index++, reference);
        }
      }

      final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
      for(Document document:InjectedLanguageUtil.getCachedInjectedDocuments(file)) {
        PsiFile injectedfile = psiDocumentManager.getPsiFile(document);
        if (injectedfile == null) continue;
        unfastenBelts(injectedfile, 0);
      }

      int size = pointers.size();
      for (int i = size - 1; i >= index; i--) {
        pointers.remove(i);
      }
    }
  }

  public static void synchronizePointers(PsiFile file) {
    final Set<Language> languages = file.getViewProvider().getLanguages();
    for (Language language : languages) {
      final PsiFile f = file.getViewProvider().getPsi(language);
      synchronized (f) {
        _synchronizePointers(f);
      }
    }
  }

  private static void _synchronizePointers(final PsiFile file) {
    List<WeakReference<SmartPointerEx>> pointers = getPointers(file);
    if (pointers == null) return;

    int index = 0;
    for (int i = 0; i < pointers.size(); i++) {
      WeakReference<SmartPointerEx> reference = pointers.get(i);
      SmartPointerEx pointer = reference.get();
      if (pointer != null) {
        pointer.documentAndPsiInSync();
        pointers.set(index++, reference);
      }
    }

    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
    for(Document document:InjectedLanguageUtil.getCachedInjectedDocuments(file)) {
      PsiFile injectedfile = psiDocumentManager.getPsiFile(document);
      if (injectedfile == null) continue;
      _synchronizePointers(injectedfile);
    }

    int size = pointers.size();
    for (int i = size - 1; i >= index; i--) {
      pointers.remove(i);
    }
  }

  //private static final Key<SmartPsiElementPointer> CACHED_SMART_POINTER_KEY = Key.create("CACHED_SMART_POINTER_KEY");
  @NotNull
  public <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element) {
    if (!element.isValid()) {
      LOG.error("Invalid element:" + element);
    }
    //final SmartPsiElementPointer cachedPointer = element.getUserData(CACHED_SMART_POINTER_KEY);
    //if (cachedPointer != null) {
    //  return cachedPointer;
    //}

    PsiFile containingFile = element.getContainingFile();
    SmartPointerEx<E> pointer = new SmartPsiElementPointerImpl<E>(myProject, element, containingFile);
    initPointer(pointer, containingFile);
    //element.putUserData(CACHED_SMART_POINTER_KEY, pointer);
    return pointer;
  }

  @Override
  @NotNull
  public SmartPsiFileRange createSmartPsiFileRangePointer(@NotNull PsiFile file, @NotNull TextRange range) {
    if (!file.isValid()) {
      LOG.error("Invalid element:" + file);
    }

    SmartPsiFileRangePointerImpl pointer = new SmartPsiFileRangePointerImpl(file, range);
    initPointer(pointer, file);

    return pointer;
  }

  private static <E extends PsiElement> void initPointer(SmartPointerEx<E> pointer, PsiFile containingFile) {
    if (containingFile == null) return;
    synchronized (containingFile) {
      //Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(containingFile);
      //todo
      //if (document != null) {
      //  //[ven] this is a really NASTY hack; when no smart pointer is kept on UsageInfo then remove this conditional
      //  if (!(element instanceof PsiFile)) {
      //    PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject);
      //    LOG.assertTrue(!documentManager.isUncommited(document) || documentManager.isCommittingDocument(document), "Document for : " +
      //                                                                                                              containingFile + " is not committed");
      //  }
      //}

      List<WeakReference<SmartPointerEx>> pointers = getPointers(containingFile);
      if (pointers == null) {
        pointers = new ArrayList<WeakReference<SmartPointerEx>>();
        containingFile.putUserData(SMART_POINTERS_IN_PSI_FILE_KEY, pointers);
      }
      pointers.add(new WeakReference<SmartPointerEx>(pointer));

      if (areBeltsFastened(containingFile)) {
        pointer.fastenBelt(0);
      }
    }
  }

  private static List<WeakReference<SmartPointerEx>> getPointers(@NotNull PsiFile containingFile) {
    return containingFile.getUserData(SMART_POINTERS_IN_PSI_FILE_KEY);
  }

  private static boolean areBeltsFastened(final PsiFile file) {
    return file.getUserData(BELTS_ARE_FASTEN_KEY) == Boolean.TRUE;
  }


  @NotNull
  @Deprecated
  public <E extends PsiElement> SmartPsiElementPointer<E> createLazyPointer(@NotNull E element) {
    return createSmartPsiElementPointer(element);
  }

  @Override
  public boolean pointToTheSameElement(@NotNull SmartPsiElementPointer pointer1, @NotNull SmartPsiElementPointer pointer2) {
    if (pointer1 instanceof SmartPsiElementPointerImpl && pointer2 instanceof SmartPsiElementPointerImpl) {
      SmartPointerElementInfo elementInfo1 = ((SmartPsiElementPointerImpl)pointer1).getElementInfo();
      SmartPointerElementInfo elementInfo2 = ((SmartPsiElementPointerImpl)pointer2).getElementInfo();
      return elementInfo1.pointsToTheSameElementAs(elementInfo2);
    }
    return Comparing.equal(pointer1.getElement(), pointer2.getElement());
  }
}
