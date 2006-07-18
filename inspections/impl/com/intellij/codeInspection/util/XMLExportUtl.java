/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 23, 2002
 * Time: 2:36:58 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.util;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiFormatUtil;
import org.jdom.Element;

@SuppressWarnings({"HardCodedStringLiteral"})
public class XMLExportUtl {
    private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.util.XMLExportUtl");

    private XMLExportUtl() {
    }

    public static Element createElement(RefEntity refEntity, Element parentNode, int actualLine, final TextRange range) {
    if (refEntity instanceof RefImplicitConstructor) {
      return createElement(refEntity.getOwner(), parentNode, actualLine, range);
    }

    Element problem = new Element(InspectionsBundle.message("inspection.export.results.problem"));

    if (refEntity instanceof RefElement) {
      final RefElement refElement = (RefElement)refEntity;
      PsiElement psiElement = refElement.getElement();
      PsiFile psiFile = psiElement.getContainingFile();

      Element fileElement = new Element(InspectionsBundle.message("inspection.export.results.file"));
      Element lineElement = new Element(InspectionsBundle.message("inspection.export.results.line"));
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      fileElement.addContent(virtualFile.getUrl());

      if (actualLine == -1) {
        Document document = PsiDocumentManager.getInstance(refElement.getRefManager().getProject()).getDocument(psiFile);
        lineElement.addContent(String.valueOf(document.getLineNumber(psiElement.getTextOffset()) + 1));
      } else {
        lineElement.addContent(String.valueOf(actualLine));
      }

      problem.addContent(fileElement);
      problem.addContent(lineElement);

      final Element rangeElement = new Element("text_range");
      final PsiElement navigationElement = HighlightUsagesHandler.getNameIdentifier(psiElement);
      final TextRange textRange = navigationElement != null 
                                  ? navigationElement.getTextRange()
                                  : range != null ? range : psiElement.getTextRange();
      rangeElement.setAttribute("start", String.valueOf(textRange.getStartOffset()));
      rangeElement.setAttribute("end", String.valueOf(textRange.getEndOffset()));
      problem.addContent(rangeElement);
    }


    if (refEntity instanceof RefMethod) {
      RefMethod refMethod = (RefMethod) refEntity;
      appendMethod(refMethod, problem);
    } else if (refEntity instanceof RefField) {
      RefField refField = (RefField) refEntity;
      appendField(refField, problem);
    } else if (refEntity instanceof RefClass) {
      RefClass refClass = (RefClass)refEntity;
      appendClass(refClass, problem);
    } else {
      LOG.info("Unknown refElement: " + refEntity);
    }
    parentNode.addContent(problem);

    return problem;
  }

  private static void appendClass(RefClass refClass, Element parentNode) {
    PsiClass psiClass = refClass.getElement();
    PsiDocComment psiDocComment = psiClass.getDocComment();

    PsiFile psiFile = psiClass.getContainingFile();

    if (psiFile instanceof PsiJavaFile) {
      String packageName = ((PsiJavaFile)psiFile).getPackageName();
      Element packageElement = new Element(InspectionsBundle.message("inspection.export.results.package"));
      packageElement.addContent(packageName.length() > 0 ? packageName : InspectionsBundle.message("inspection.export.results.default"));
      parentNode.addContent(packageElement);
    }

    Element classElement = new Element(InspectionsBundle.message("inspection.export.results.class"));
    if (psiDocComment != null) {
      PsiDocTag[] tags = psiDocComment.getTags();
      for (PsiDocTag tag : tags) {
        if (InspectionsBundle.message("inspection.export.results.author").equals(tag.getName()) && tag.getValueElement() != null) {
          classElement.setAttribute(InspectionsBundle.message("inspection.export.results.author"), tag.getValueElement().getText());
        }
      }
    }

    String name = PsiFormatUtil.formatClass(psiClass, PsiFormatUtil.SHOW_NAME);
    Element nameElement = new Element(InspectionsBundle.message("inspection.export.results.name"));
    nameElement.addContent(name);
    classElement.addContent(nameElement);

    Element displayName = new Element(InspectionsBundle.message("inspection.export.results.display.name"));
    final RefUtil refUtil = RefUtil.getInstance();
    displayName.addContent(refUtil.getQualifiedName(refClass));
    classElement.addContent(displayName);

    parentNode.addContent(classElement);

    RefClass topClass = refUtil.getTopLevelClass(refClass);
    if (topClass != refClass) {
      appendClass(topClass, classElement);
    }
  }

  private static void appendMethod(final RefMethod refMethod, Element parentNode) {
    Element methodElement = new Element(refMethod.isConstructor() ? InspectionsBundle.message("inspection.export.results.constructor") : InspectionsBundle.message("inspection.export.results.method"));

    PsiMethod psiMethod = (PsiMethod) refMethod.getElement();
    String name = PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME |
                                                        PsiFormatUtil.SHOW_FQ_NAME |
                                                        PsiFormatUtil.SHOW_TYPE |
                                                        PsiFormatUtil.SHOW_PARAMETERS,
                                             PsiFormatUtil.SHOW_NAME |
                                             PsiFormatUtil.SHOW_TYPE
    );

    Element shortNameElement = new Element(InspectionsBundle.message("inspection.export.results.name"));
    shortNameElement.addContent(name);
    methodElement.addContent(shortNameElement);

    Element displayName = new Element(InspectionsBundle.message("inspection.export.results.display.name"));
    final RefUtil refUtil = RefUtil.getInstance();
    displayName.addContent(refUtil.getQualifiedName(refMethod));
    methodElement.addContent(displayName);

    appendClass(refUtil.getTopLevelClass(refMethod), methodElement);

    parentNode.addContent(methodElement);
  }

  private static void appendField(final RefField refField, Element parentNode) {
    Element fieldElement = new Element(InspectionsBundle.message("inspection.export.results.field"));
    PsiField psiField = refField.getElement();
    String name = PsiFormatUtil.formatVariable(psiField, PsiFormatUtil.SHOW_NAME |
                                                         PsiFormatUtil.SHOW_TYPE,
        PsiSubstitutor.EMPTY);

    Element shortNameElement = new Element(InspectionsBundle.message("inspection.export.results.name"));
    shortNameElement.addContent(name);
    fieldElement.addContent(shortNameElement);

    Element displayName = new Element(InspectionsBundle.message("inspection.export.results.display.name"));
    final RefUtil refUtil = RefUtil.getInstance();
    displayName.addContent(refUtil.getQualifiedName(refField));
    fieldElement.addContent(displayName);

    appendClass(refUtil.getTopLevelClass(refField), fieldElement);

    parentNode.addContent(fieldElement);
  }
}
