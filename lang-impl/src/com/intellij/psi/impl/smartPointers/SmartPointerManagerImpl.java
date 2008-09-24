package com.intellij.psi.impl.smartPointers;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Set;

public class SmartPointerManagerImpl extends SmartPointerManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl");

  private static final Key<ArrayList<WeakReference<SmartPointerEx>>> SMART_POINTERS_IN_PSI_FILE_KEY = Key.create(
    "SMART_POINTERS_IN_DOCUMENT_KEY");
  private static final Key<Boolean> BELTS_ARE_FASTEN_KEY = Key.create("BELTS_ARE_FASTEN_KEY");

  private final Project myProject;

  public SmartPointerManagerImpl(Project project) {
    myProject = project;
  }

  public static void fastenBelts(PsiFile file) {
    synchronized (file) {
      if (areBeltsFastened(file)) return;

      file.putUserData(BELTS_ARE_FASTEN_KEY, Boolean.TRUE);

      ArrayList<WeakReference<SmartPointerEx>> pointers = file.getUserData(SMART_POINTERS_IN_PSI_FILE_KEY);
      if (pointers == null) return;

      int index = 0;
      for (int i = 0; i < pointers.size(); i++) {
        WeakReference<SmartPointerEx> reference = pointers.get(i);
        SmartPointerEx pointer = reference.get();
        if (pointer != null) {
          pointer.fastenBelt();
          pointers.set(index++, reference);
        }
      }

      final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
      for(Document document:InjectedLanguageUtil.getCachedInjectedDocuments(file)) {
        PsiFile injectedfile = psiDocumentManager.getPsiFile(document);
        if (injectedfile == null) continue;
        fastenBelts(injectedfile);
      }

      int size = pointers.size();
      for (int i = size - 1; i >= index; i--) {
        pointers.remove(i);
      }
    }
  }

  public static void unfastenBelts(PsiFile file) {
    final Set<Language> languages = file.getViewProvider().getLanguages();
    for (Language language : languages) {
      final PsiFile f = file.getViewProvider().getPsi(language);
      synchronized (f) {
        f.putUserData(BELTS_ARE_FASTEN_KEY, null);
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
    ArrayList<WeakReference<SmartPointerEx>> pointers = file.getUserData(SMART_POINTERS_IN_PSI_FILE_KEY);
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

  private class IdentitySmartPointer<T extends PsiElement> implements SmartPointerEx<T> {
    private T myElement;

    public IdentitySmartPointer(final T element) {
      myElement = element;
    }

    public T getElement() {
      if (myElement != null && !myElement.isValid()) {
        myElement = null;
      }
      return myElement;
    }

    public int hashCode() {
      final T elt = getElement();
      return elt == null ? 0 : elt.hashCode();
    }

    public boolean equals(Object obj) {
      return obj instanceof SmartPsiElementPointer && Comparing.equal(getElement(), ((SmartPsiElementPointer)obj).getElement());
    }

    public PsiFile getContainingFile() {
      final T elt = getElement();
      return elt != null ? elt.getContainingFile() : null;
    }

    public void documentAndPsiInSync() {}

    public void fastenBelt() {}
  }

  @NotNull
  public <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(E element) {
    if (!element.isValid()) {
      LOG.assertTrue(false, "Invalid element:" + element);
    }

    PsiFile file = element.getContainingFile();

    if (isSafeReparseable(file)) {
      return new IdentitySmartPointer<E>(element);
    }

    SmartPointerEx<E> pointer = new SmartPsiElementPointerImpl<E>(myProject, element);
    initPointer(element, pointer);

    return pointer;
  }

  private static boolean isSafeReparseable(final PsiFile file) {
    return false;
    //return file != null && !(file instanceof CodeFragmentElement);  TODO: needs proper processing of foreign chameleons on reparse (like javascript in HTML).
  }

  private <E extends PsiElement> void initPointer(E element, SmartPointerEx<E> pointer) {
    PsiFile file = element.getContainingFile();
    if (file != null) {
      synchronized (file) {
        Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
        if (document != null) {
          //[ven] this is a really NASTY hack; when no smart pointer is kept on UsageInfo then remove this conditional
          if (!(element instanceof PsiFile)) {
            PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject);
            LOG.assertTrue(!documentManager.isUncommited(document) || documentManager.isCommittingDocument(document), "Document for : " + file + " is not committed");
          }
        }

        ArrayList<WeakReference<SmartPointerEx>> pointers = file.getUserData(SMART_POINTERS_IN_PSI_FILE_KEY);
        if (pointers == null) {
          pointers = new ArrayList<WeakReference<SmartPointerEx>>();
          file.putUserData(SMART_POINTERS_IN_PSI_FILE_KEY, pointers);
        }
        pointers.add(new WeakReference<SmartPointerEx>(pointer));

        if (areBeltsFastened(file)) {
          pointer.fastenBelt();
        }
      }
    }
  }

  private static boolean areBeltsFastened(final PsiFile file) {
    return file.getUserData(BELTS_ARE_FASTEN_KEY) == Boolean.TRUE;
  }


  @NotNull
  public <E extends PsiElement> SmartPsiElementPointer<E> createLazyPointer(E element) {
    LazyPointerImpl<E> pointer = new LazyPointerImpl<E>(element);
    initPointer(element, pointer);
    return pointer;
  }
}
