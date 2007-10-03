/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 23, 2002
 * Time: 2:36:58 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.util;

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

    Element problem = new Element("problem");

    if (refEntity instanceof RefElement) {
      final RefElement refElement = (RefElement)refEntity;
      PsiElement psiElement = refElement.getElement();
      PsiFile psiFile = psiElement.getContainingFile();

      Element fileElement = new Element("file");
      Element lineElement = new Element("line");
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      LOG.assertTrue(virtualFile != null);
      fileElement.addContent(virtualFile.getUrl());

      if (actualLine == -1) {
        final Document document = PsiDocumentManager.getInstance(refElement.getRefManager().getProject()).getDocument(psiFile);
        LOG.assertTrue(document != null);
        lineElement.addContent(String.valueOf(document.getLineNumber(psiElement.getTextOffset()) + 1));
      }
      else {
        lineElement.addContent(String.valueOf(actualLine));
      }

      problem.addContent(fileElement);
      problem.addContent(lineElement);
      appendModule(problem, refElement.getModule());
    }
    else if (refEntity instanceof RefModule) {
      final RefModule refModule = (RefModule)refEntity;
      final VirtualFile moduleFile = refModule.getModule().getModuleFile();
      final Element fileElement = new Element("file");
      fileElement.addContent(moduleFile != null? moduleFile.getUrl() : refEntity.getName());
      problem.addContent(fileElement);
      appendModule(problem, refModule);
      appendFakePackage(problem);
    } else if (refEntity instanceof RefPackage) {
      Element packageElement = new Element("package");
      packageElement.addContent(refEntity.getName());
      problem.addContent(packageElement);
    }

    new SmartRefElementPointerImpl(refEntity, true).writeExternal(problem);

    if (refEntity instanceof RefMethod) {
      RefMethod refMethod = (RefMethod)refEntity;
      appendMethod(refMethod, problem);
    }
    else if (refEntity instanceof RefField) {
      RefField refField = (RefField)refEntity;
      appendField(refField, problem);
    }
    else if (refEntity instanceof RefClass) {
      RefClass refClass = (RefClass)refEntity;
      appendClass(refClass, problem);
    } else if (refEntity instanceof RefFile) {
      appendFakePackage(problem);
    }
    parentNode.addContent(problem);

    return problem;
  }

  private static void appendModule(final Element problem, final RefModule refModule) {
    if (refModule != null) {
      Element moduleElement = new Element("module");
      moduleElement.addContent(refModule.getName());
      problem.addContent(moduleElement);
    }
  }

  private static void appendFakePackage(final Element problem) {
    final Element fakePackage = new Element("package");
    fakePackage.addContent(InspectionsBundle.message("inspection.export.results.default"));
    problem.addContent(fakePackage);
  }

  private static void appendClass(RefClass refClass, Element parentNode) {
    PsiClass psiClass = refClass.getElement();
    PsiDocComment psiDocComment = psiClass.getDocComment();

    PsiFile psiFile = psiClass.getContainingFile();

    if (psiFile instanceof PsiJavaFile) {
      String packageName = ((PsiJavaFile)psiFile).getPackageName();
      Element packageElement = new Element("package");
      packageElement.addContent(packageName.length() > 0 ? packageName : InspectionsBundle.message("inspection.export.results.default"));
      parentNode.addContent(packageElement);
    }

    Element classElement = new Element("class");
    if (psiDocComment != null) {
      PsiDocTag[] tags = psiDocComment.getTags();
      for (PsiDocTag tag : tags) {
        if ("author".equals(tag.getName()) && tag.getValueElement() != null) {
          classElement.setAttribute("author", tag.getValueElement().getText());
        }
      }
    }

    String name = PsiFormatUtil.formatClass(psiClass, PsiFormatUtil.SHOW_NAME);
    Element nameElement = new Element("name");
    nameElement.addContent(name);
    classElement.addContent(nameElement);

    Element displayName = new Element("display_name");
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
    Element methodElement = new Element(refMethod.isConstructor() ? "constructor" : "method");

    PsiMethod psiMethod = (PsiMethod)refMethod.getElement();
    String name = PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_FQ_NAME |
                                                                              PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.SHOW_PARAMETERS,
                                                                              PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE);

    Element shortNameElement = new Element("name");
    shortNameElement.addContent(name);
    methodElement.addContent(shortNameElement);

    Element displayName = new Element("name");
    final RefUtil refUtil = RefUtil.getInstance();
    displayName.addContent(refUtil.getQualifiedName(refMethod));
    methodElement.addContent(displayName);

    appendClass(refUtil.getTopLevelClass(refMethod), methodElement);

    parentNode.addContent(methodElement);
  }

  private static void appendField(final RefField refField, Element parentNode) {
    Element fieldElement = new Element("field");
    PsiField psiField = refField.getElement();
    String name = PsiFormatUtil.formatVariable(psiField, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE, PsiSubstitutor.EMPTY);

    Element shortNameElement = new Element("name");
    shortNameElement.addContent(name);
    fieldElement.addContent(shortNameElement);

    Element displayName = new Element("display_name");
    final RefUtil refUtil = RefUtil.getInstance();
    displayName.addContent(refUtil.getQualifiedName(refField));
    fieldElement.addContent(displayName);

    appendClass(refUtil.getTopLevelClass(refField), fieldElement);

    parentNode.addContent(fieldElement);
  }
}
