package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.LookupValueWithPsiElement;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;

public class TargetElementUtil {
  public static final int REFERENCED_ELEMENT_ACCEPTED = 0x01;
  public static final int ELEMENT_NAME_ACCEPTED = 0x02;
  public static final int NEW_AS_CONSTRUCTOR = 0x04;
  public static final int LOOKUP_ITEM_ACCEPTED = 0x08;
  public static final int THIS_ACCEPTED = 0x10;
  public static final int SUPER_ACCEPTED = 0x20;
  public static final int TRY_ACCEPTED = 0x40;
  public static final int CATCH_ACCEPTED = 0x80;
  public static final int THROW_ACCEPTED = 0x100;
  public static final int THROWS_ACCEPTED = 0x200;
  public static final int RETURN_ACCEPTED = 0x400;
  public static final int THROW_STATEMENT_ACCEPTED = 0x800;

  public static PsiElement findTargetElement(Editor editor, int flags) {
    int offset = editor.getCaretModel().getOffset();
    return findTargetElement(editor, flags, offset);
  }

  public static PsiReference findReference(Editor editor) {
    return findReference(editor, editor.getCaretModel().getOffset());
  }

  public static PsiReference findReference(Editor editor, int offset) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    DataContext dataContext = DataManager.getInstance().getDataContext(editor.getComponent());
    Project project = (Project) dataContext.getData(DataConstants.PROJECT);
    if (project == null) return null;

    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return null;
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    offset = adjustOffset(document, offset);

    if (file instanceof PsiCompiledElement) {
      return ((PsiCompiledElement) file).getMirror().findReferenceAt(offset);
    }

    return file.findReferenceAt(offset);
  }

  public static PsiElement findTargetElement(Editor editor, int flags, int offset) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    DataContext dataContext = DataManager.getInstance().getDataContext(editor.getComponent());
    Project project = (Project) dataContext.getData(DataConstants.PROJECT);
    if (project == null) return null;

    Lookup activeLookup = LookupManager.getInstance(project).getActiveLookup();
    if (activeLookup != null && (flags & LOOKUP_ITEM_ACCEPTED) != 0) {
      final PsiElement lookupItem = getLookupItem(activeLookup);
      return lookupItem != null && lookupItem.isValid() ? lookupItem : null;
    }

    Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) return null;

    offset = adjustOffset(document, offset);

    /*
    if (file instanceof PsiCompiledElement) {
      file = (PsiFile)((PsiCompiledElement) file).getMirror();
    }
    */

    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;

    if ((flags & ELEMENT_NAME_ACCEPTED) != 0) {
      PsiElement parent = element.getParent();
      if (element instanceof PsiIdentifier) {
        if (parent instanceof PsiClass && element.equals(((PsiClass) parent).getNameIdentifier())) {
          return parent;
        }
        else if (parent instanceof PsiVariable && element.equals(((PsiVariable) parent).getNameIdentifier())) {
          return parent;
        }
        else if (parent instanceof PsiMethod && element.equals(((PsiMethod) parent).getNameIdentifier())) {
          return parent;
        }
        else if (parent instanceof PsiLabeledStatement && element.equals(((PsiLabeledStatement)parent).getLabelIdentifier())) {
          return parent;
        }
      }
      else if (parent instanceof PsiNamedElement) { // A bit hacky depends on navigation offset correctly overriden
        if (parent.getTextOffset() == element.getTextOffset() &&
            Comparing.equal(((PsiNamedElement)parent).getName(), element.getText()) &&
            !(parent instanceof XmlAttribute)) {
          return parent;
        }
      }
    }

    if (element instanceof PsiKeyword) {
      if (element.getParent() instanceof PsiThisExpression) {
        if ((flags & THIS_ACCEPTED) == 0) return null;
        PsiType type = ((PsiThisExpression) element.getParent()).getType();
        if (!(type instanceof PsiClassType)) return null;
        return ((PsiClassType) type).resolve();
      }

      if (element.getParent() instanceof PsiSuperExpression) {
        if ((flags & SUPER_ACCEPTED) == 0) return null;
        PsiType type = ((PsiSuperExpression) element.getParent()).getType();
        if (!(type instanceof PsiClassType)) return null;
        return ((PsiClassType) type).resolve();
      }

      if (PsiKeyword.TRY.equals(element.getText())) {
        if ((flags & TRY_ACCEPTED) == 0) return null;
        return element;
      }

      if (PsiKeyword.CATCH.equals(element.getText())) {
        if ((flags & CATCH_ACCEPTED) == 0) return null;
        return element;
      }

      if (PsiKeyword.THROWS.equals(element.getText())) {
        if ((flags & THROWS_ACCEPTED) == 0) return null;
        return element;
      }

      if (PsiKeyword.THROW.equals(element.getText())) {
        if ((flags & THROW_ACCEPTED) != 0) return element;
        if ((flags & THROW_STATEMENT_ACCEPTED) != 0) {
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiThrowStatement) {
            return parent;
          }
        }
        return null;
      }

      if (PsiKeyword.RETURN.equals(element.getText())) {
        if ((flags & RETURN_ACCEPTED) == 0) return null;
        return element;
      }
    }

    if ((flags & REFERENCED_ELEMENT_ACCEPTED) != 0) {
      final PsiElement referenceOrReferencedElement = getReferenceOrReferencedElement(file, editor, flags, offset);
      //if (referenceOrReferencedElement == null) {
      //  return getReferenceOrReferencedElement(file, editor, flags, offset);
      //}
      if (referenceOrReferencedElement != null && referenceOrReferencedElement.isValid()) return referenceOrReferencedElement;
    }

    return null;
  }


  private static int adjustOffset(Document document, final int offset) {
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

  private static PsiElement getReferenceOrReferencedElement(PsiFile file, Editor editor, int flags, int offset) {
    PsiReference ref = findReference(editor, offset);
    if (ref == null) return null;
    PsiManager manager = file.getManager();

    final PsiElement referenceElement = ref.getElement();
    PsiElement refElement;
    if(ref instanceof PsiJavaReference){
      refElement = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    }
    else{
      refElement = ref.resolve();
    }

    if (refElement == null) {
      DaemonCodeAnalyzer.getInstance(manager.getProject()).updateVisibleHighlighters(editor);
      return null;
    }
    else {
      if ((flags & NEW_AS_CONSTRUCTOR) != 0) {
        PsiElement parent = referenceElement.getParent();
        if (parent instanceof PsiAnonymousClass) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiNewExpression) {
          PsiMethod constructor = ((PsiNewExpression) parent).resolveConstructor();
          if (constructor != null) {
            refElement = constructor;
          }
        }
      }
      if (refElement instanceof PsiClass && refElement.getContainingFile().getVirtualFile() == null) { // in mirror file of compiled class
        return manager.findClass(((PsiClass) refElement).getQualifiedName(), refElement.getResolveScope());
      }
      return refElement;
    }
  }

  private static PsiElement getLookupItem(Lookup activeLookup) {
    LookupItem item = activeLookup.getCurrentItem();
    if (item == null) return null;
    Object o = item.getObject();

    if (o instanceof PsiClass
        || o instanceof PsiPackage
        || o instanceof PsiMethod
        || o instanceof PsiVariable
        || o instanceof PsiFile
      ) {
      PsiElement element = (PsiElement) o;
      if (!(element instanceof PsiPackage)) {
        if (!isValidElement(element)) return null;
      }
      return element;
    }
    else if (o instanceof LookupValueWithPsiElement) {
      final PsiElement element = ((LookupValueWithPsiElement)o).getElement();
      if (isValidElement(element)) return element;
      return null;
    } else {
      return null;
    }
  }

  private static boolean isValidElement(PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) return false;
    if (file.getOriginalFile() != null) file = file.getOriginalFile();
    return file != null && file.getVirtualFile() != null;
  }
}
