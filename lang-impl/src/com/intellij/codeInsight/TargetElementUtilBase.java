/*
 * User: anna
 * Date: 30-Jan-2008
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.LookupValueWithPsiElement;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class TargetElementUtilBase {
  public static final int REFERENCED_ELEMENT_ACCEPTED = 0x01;
  public static final int ELEMENT_NAME_ACCEPTED = 0x02;
  public static final int LOOKUP_ITEM_ACCEPTED = 0x08;

  public static TargetElementUtilBase getInstance() {
    return ServiceManager.getService(TargetElementUtilBase.class);
  }

  public int getAllAccepted() {
    return REFERENCED_ELEMENT_ACCEPTED | ELEMENT_NAME_ACCEPTED | LOOKUP_ITEM_ACCEPTED;
  }

  /**
   * Accepts THIS or SUPER but not NEW_AS_CONSTRUCTOR.
   */
  public int getDefinitionSearchFlags() {
    return getAllAccepted();
  }

  /**
   * Accepts NEW_AS_CONSTRUCTOR but not THIS or SUPER.
   */
  public int getReferenceSearchFlags() {
    return getAllAccepted();
  }

  @Nullable
  public static PsiReference findReference(Editor editor) {
    return findReference(editor, editor.getCaretModel().getOffset());
  }

  @Nullable
  public static PsiReference findReference(Editor editor, int offset) {
    Project project = editor.getProject();
    if (project == null) return null;

    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return null;
    if (ApplicationManager.getApplication().isDispatchThread()) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    }

    offset = adjustOffset(document, offset);

    if (file instanceof PsiCompiledElement) {
      return ((PsiCompiledElement)file).getMirror().findReferenceAt(offset);
    }

    return file.findReferenceAt(offset);
  }

  public static int adjustOffset(Document document, final int offset) {
    CharSequence text = document.getCharsSequence();
    int correctedOffset = offset;
    int textLength = document.getTextLength();
    if (offset >= textLength) {
      correctedOffset = textLength - 1;
    }
    else {
      if (!Character.isJavaIdentifierPart(text.charAt(offset))) {
        correctedOffset--;
      }
    }
    if (correctedOffset < 0 || !Character.isJavaIdentifierPart(text.charAt(correctedOffset))) return offset;
    return correctedOffset;
  }

  @Nullable
  public static PsiElement findTargetElement(Editor editor, int flags) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int offset = editor.getCaretModel().getOffset();
    return getInstance().findTargetElement(editor, flags, offset);
  }

  @Nullable
  public PsiElement findTargetElement(Editor editor, int flags, int offset) {
    Project project = editor.getProject();
    if (project == null) return null;

    Lookup activeLookup = LookupManager.getInstance(project).getActiveLookup();
    if (activeLookup != null && (flags & LOOKUP_ITEM_ACCEPTED) != 0) {
      final PsiElement lookupItem = getLookupItem(activeLookup);
      return lookupItem != null && lookupItem.isValid() ? lookupItem : null;
    }

    Document document = editor.getDocument();
    if (ApplicationManager.getApplication().isDispatchThread()) {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    }
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return null;

    offset = adjustOffset(document, offset);

    PsiElement element = file.findElementAt(offset);
    if ((flags & REFERENCED_ELEMENT_ACCEPTED) != 0) {
      final PsiElement referenceOrReferencedElement = getReferenceOrReferencedElement(file, editor, flags, offset);
      //if (referenceOrReferencedElement == null) {
      //  return getReferenceOrReferencedElement(file, editor, flags, offset);
      //}
      if (isAcceptableReferencedElement(element, referenceOrReferencedElement)) {
        return referenceOrReferencedElement;
      }
    }

    if (element == null) return null;

    if ((flags & ELEMENT_NAME_ACCEPTED) != 0) {
      if (element instanceof PsiNamedElement) return element;
      return getNamedElement(element);
    }
    return null;
  }

  protected boolean isAcceptableReferencedElement(final PsiElement element, final PsiElement referenceOrReferencedElement) {
    return referenceOrReferencedElement != null &&
        referenceOrReferencedElement.isValid();
  }

  @Nullable
  public PsiElement adjustElement(final Editor editor, final int flags, final PsiElement element, final PsiElement contextElement) {
    return element;
  }

  @Nullable
  public PsiElement adjustReference(@NotNull PsiReference ref){
    return null;
  }

  @Nullable
  protected PsiElement getNamedElement(@Nullable final PsiElement element) {
    PsiElement parent;
    if ((parent = PsiTreeUtil.getParentOfType(element, PsiNamedElement.class, false)) != null) {
      // A bit hacky depends on navigation offset correctly overridden
      assert element != null : "notnull parent?";
      if (parent.getTextOffset() == element.getTextRange().getStartOffset()) {
        return parent;
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement getLookupItem(Lookup activeLookup) {
    LookupElement item = activeLookup.getCurrentItem();
    if (item == null) return null;
    Object o = item.getObject();

    if (o instanceof PsiElement) {
      PsiElement element = (PsiElement)o;
      if (!(element instanceof PsiDirectoryContainer)) {
        if (!isValidElement(element)) return null;
      }
      return element;
    }
    else if (o instanceof LookupValueWithPsiElement) {
      final PsiElement element = ((LookupValueWithPsiElement)o).getElement();
      if (element != null && isValidElement(element)) return element;
    }
    return null;
  }

  private static boolean isValidElement(@NotNull PsiElement element) {
    if (!element.isValid()) return false;
    PsiFile file = element.getContainingFile();
    if (file == null) return false;
    if (file.getOriginalFile() != null) file = file.getOriginalFile();
    return file != null && file.getVirtualFile() != null;
  }

  @Nullable
  protected PsiElement getReferenceOrReferencedElement(PsiFile file, Editor editor, int flags, int offset) {
    PsiReference ref = findReference(editor, offset);
    if (ref == null) return null;
    PsiManager manager = file.getManager();
    PsiElement refElement = ref.resolve();
    if (refElement == null) {
      DaemonCodeAnalyzer.getInstance(manager.getProject()).updateVisibleHighlighters(editor);
      return null;
    }
    else {
      return refElement;
    }
  }

  public Collection<PsiElement> getTargetCandidates(PsiReference reference) {
    if (reference instanceof PsiPolyVariantReference) {
      final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
      final ArrayList<PsiElement> navigatableResults = new ArrayList<PsiElement>(results.length);

      for(ResolveResult r:results) {
        PsiElement element = r.getElement();
        if (EditSourceUtil.canNavigate(element) || element instanceof Navigatable && ((Navigatable)element).canNavigateToSource()) {
          navigatableResults.add(element);
        }
      }

      return navigatableResults;
    }
    PsiElement resolved = reference.resolve();
    if (resolved instanceof NavigationItem) {
      return Collections.singleton(resolved);
    }
    return Collections.emptyList();
  }

  public PsiElement getGotoDeclarationTarget(final PsiElement element, final PsiElement navElement) {
    return navElement;
  }

  public boolean includeSelfInGotoImplementation(final PsiElement element) {
    return true;
  }
}
