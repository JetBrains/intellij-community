// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class JavaQualifiedNameProvider implements QualifiedNameProvider {
  private static final Logger LOG = Logger.getInstance(JavaQualifiedNameProvider.class);

  @Override
  @Nullable
  public PsiElement adjustElementToCopy(@NotNull PsiElement element) {
    if (element instanceof PsiPackage) return element;
    if (element instanceof PsiDirectory) {
      final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)element);
      if (psiPackage != null) return psiPackage;
    }
    if (!(element instanceof PsiMember) && element.getParent() instanceof PsiMember) {
      return element.getParent();
    }
    return null;
  }

  @Override
  @Nullable
  public String getQualifiedName(@NotNull PsiElement element) {
    if (element instanceof PsiPackage pkg) {
      return pkg.getQualifiedName();
    }

    if (element instanceof PsiJavaModule module) {
      return module.getName();
    }

    if (element instanceof PsiJavaModuleReferenceElement ref) {
      PsiJavaModuleReference reference = ref.getReference();
      if (reference != null) {
        PsiJavaModule target = reference.resolve();
        if (target != null) {
          return target.getName();
        }
      }
    }

    element = getMember(element);
    if (element instanceof PsiClass) {
      return ((PsiClass)element).getQualifiedName();
    }
    else if (element instanceof PsiMember member) {
      String memberFqn = getMethodOrFieldQualifiedName(member);
      if (memberFqn == null) return null;
      if (member instanceof PsiMethod method && MethodSignatureUtil.hasOverloads(method)) {
        return memberFqn + getParameterString(method);
      }
      return memberFqn;
    }

    return null;
  }

  private static String getMethodOrFieldQualifiedName(@NotNull PsiMember member) {
    PsiClass containingClass = member.getContainingClass();
    if (containingClass instanceof PsiAnonymousClass) containingClass = ((PsiAnonymousClass)containingClass).getBaseClassType().resolve();
    if (containingClass == null) return null;
    String classFqn = containingClass.getQualifiedName();
    if (classFqn == null) return member.getName();  // refer to member of anonymous class by simple name
    return classFqn + "#" + member.getName();
  }

  @Override
  public PsiElement qualifiedNameToElement(@NotNull String fqn, @NotNull Project project) {
    final PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(fqn);
    if (psiPackage != null) {
      return psiPackage;
    }
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
    if (aClass != null) {
      return aClass;
    }
    final int endIndex = fqn.indexOf('#');
    if (endIndex != -1) {
      String className = fqn.substring(0, endIndex);
      int paramIndex = fqn.indexOf('(', endIndex);
      aClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
      if (aClass != null) {
        String memberName = fqn.substring(endIndex + 1, paramIndex < 0 ? fqn.length() : paramIndex);
        PsiField field = aClass.findFieldByName(memberName, false);
        if (field != null) {
          return field;
        }
        String paramString = paramIndex < 0 ? "" : fqn.substring(paramIndex);
        for (PsiMethod overload : aClass.findMethodsByName(memberName, false)) {
          if (StringUtil.isEmpty(paramString) || paramString.equals(getParameterString(overload))) {
            return overload;
          }
        }
      }
    }

    VirtualFile file = findFile(fqn, project);
    if (file != null) {
      return PsiManager.getInstance(project).findFile(file);
    }
    return null;
  }

  private static VirtualFile findFile(String fqn, Project project) {
    for (VirtualFile root : ProjectRootManager.getInstance(project).getContentSourceRoots()) {
      VirtualFile rel = root.findFileByRelativePath(fqn);
      if (rel != null) {
        return rel;
      }
    }
    for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRoots()) {
      VirtualFile rel = root.findFileByRelativePath(fqn);
      if (rel != null) {
        return rel;
      }
    }
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(fqn);
    if (file != null) return file;
    PsiFile[] files = PsiShortNamesCache.getInstance(project).getFilesByName(fqn);
    for (PsiFile psiFile : files) {
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) return virtualFile;
    }
    return null;
  }

  @Override
  public void insertQualifiedName(@NotNull String fqn, @NotNull PsiElement element, @NotNull Editor editor, @NotNull Project project) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = editor.getDocument();

    final PsiFile file = documentManager.getPsiFile(document);

    final int offset = editor.getCaretModel().getOffset();
    PsiElement elementAtCaret = file.findElementAt(offset);

    fqn = fqn.replace('#', '.');
    String toInsert;
    String suffix = "";

    if (!(element instanceof PsiMember)) {
      toInsert = fqn;
    }
    else if (elementAtCaret != null && (element instanceof PsiMethod || element instanceof PsiField) && PsiUtil.isInsideJavadocComment(elementAtCaret)) {
      // use fqn#methodName(ParamType)
      PsiMember member = (PsiMember)element;
      PsiClass aClass = member.getContainingClass();
      String className = aClass == null ? "" : aClass.getQualifiedName();
      toInsert = className == null ? "" : className;
      if (toInsert.length() != 0) toInsert += "#";
      toInsert += member.getName();
      if (member instanceof PsiMethod) {
        toInsert += getParameterString((PsiMethod)member, true);
      }
    }
    else if (elementAtCaret == null ||
             PsiTreeUtil.getNonStrictParentOfType(elementAtCaret, PsiLiteralExpression.class, PsiComment.class) != null ||
             PsiTreeUtil.getNonStrictParentOfType(elementAtCaret, PsiJavaFile.class) == null ||
             isEndOfLineComment(elementAtCaret)) {
      toInsert = fqn;
    }
    else {
      PsiMember targetElement = (PsiMember)element;

      toInsert = targetElement.getName();
      if (targetElement instanceof PsiMethod) {
        suffix = "()";
        int parenthIdx = fqn.indexOf('(');
        if (parenthIdx >= 0) {
          fqn = fqn.substring(0, parenthIdx);
        }

        if (((PsiMethod)targetElement).isConstructor()) {
          targetElement = targetElement.getContainingClass();
          fqn = StringUtil.getPackageName(fqn);
        }
      }
      else if (targetElement instanceof PsiClass) {
        if (isAfterNew(file, elementAtCaret)) {
          // pasting reference to default constructor of the class after new
          suffix = "()";
        }
        else if (toInsert != null && toInsert.length() != 0 && Character.isJavaIdentifierPart(toInsert.charAt(toInsert.length()-1)) && Character.isJavaIdentifierPart(elementAtCaret.getText().charAt(0))) {
          //separate identifiers with space
          suffix = " ";
        }
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiExpression expression;
      try {
        expression = factory.createExpressionFromText(toInsert + suffix, elementAtCaret);
        final PsiReferenceExpression referenceExpression = expression instanceof PsiMethodCallExpression
                                                           ? ((PsiMethodCallExpression)expression).getMethodExpression()
                                                           : expression instanceof PsiReferenceExpression
                                                             ? (PsiReferenceExpression)expression
                                                             : null;
        if (referenceExpression == null || !referenceExpression.isValid()) {
          toInsert = fqn;
        }
        else if (!isReferencedTo(referenceExpression, targetElement)) {
          try {
            referenceExpression.bindToElement(targetElement);
          }
          catch (IncorrectOperationException e) {
            // failed to bind
          }
          if (!referenceExpression.isValid() || !isReferencedTo(referenceExpression, targetElement)) {
            toInsert = fqn;
          }
        }
      }
      catch (IncorrectOperationException ignored) {}
    }
    if (toInsert == null) toInsert = "";

    document.insertString(offset, toInsert+suffix);
    documentManager.commitAllDocuments();
    int endOffset = offset + toInsert.length() + suffix.length();
    RangeMarker rangeMarker = document.createRangeMarker(endOffset, endOffset);
    elementAtCaret = file.findElementAt(offset);

    if (elementAtCaret != null && elementAtCaret.isValid()) {
      try {
        shortenReference(elementAtCaret);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(file);
    try {
      CodeStyleManager.getInstance(project).adjustLineIndent(file, offset);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    int caretOffset = rangeMarker.getEndOffset();
    if (element instanceof PsiMethod && !((PsiMethod)element).getParameterList().isEmpty() && StringUtil.endsWithChar(suffix,')')) {
      caretOffset --;
    }
    editor.getCaretModel().moveToOffset(caretOffset);
  }

  private static boolean isEndOfLineComment(PsiElement elementAtCaret) {
    PsiElement prevElement = PsiTreeUtil.prevLeaf(elementAtCaret);
    return prevElement instanceof PsiComment && JavaTokenType.END_OF_LINE_COMMENT.equals(((PsiComment)prevElement).getTokenType());
  }

  private static String getParameterString(PsiMethod method, final boolean erasure) {
    return "(" + StringUtil.join(method.getParameterList().getParameters(), parameter -> {
      PsiType type = parameter.getType();
      if (erasure) {
        final PsiType erased = TypeConversionUtil.erasure(type);
        if (erased != null) {
          type = erased;
        }
      }
      return type.getCanonicalText();
    }, ", ") + ")";
  }

  private static String getParameterString(PsiMethod method) {
    return getParameterString(method, false);
  }

  private static boolean isReferencedTo(PsiReferenceExpression referenceExpression, PsiMember targetElement) {
    PsiElement resolved = referenceExpression.advancedResolve(true).getElement();
    if (!(resolved instanceof PsiMember)) return false;
    PsiClass aClass = ((PsiMember)resolved).getContainingClass();
    if (aClass instanceof PsiAnonymousClass) {
      aClass = ((PsiAnonymousClass)aClass).getBaseClassType().resolve();
      return aClass == targetElement.getContainingClass();
    }
    return resolved == targetElement;
  }

  @Nullable
  private static PsiElement getMember(PsiElement element) {
    if (element instanceof PsiMember) return element;

    if (element instanceof PsiReference) {
      PsiElement resolved = ((PsiReference)element).resolve();
      if (resolved instanceof PsiMember) return resolved;
    }

    if (!(element instanceof PsiIdentifier)) return null;

    PsiElement parent = element.getParent();
    PsiMember member = null;
    if (parent instanceof PsiJavaCodeReferenceElement) {
      PsiElement resolved = ((PsiJavaCodeReferenceElement)parent).resolve();
      if (resolved instanceof PsiMember) {
        member = (PsiMember)resolved;
      }
    }
    else if (parent instanceof PsiMember) {
      member = (PsiMember)parent;
    }
    return member;
  }

  private static boolean isAfterNew(PsiFile file, PsiElement elementAtCaret) {
    PsiElement prevSibling = elementAtCaret.getPrevSibling();
    if (prevSibling == null) return false;
    int offset = prevSibling.getTextRange().getStartOffset();
    PsiElement prevElement = file.findElementAt(offset);
    return PsiTreeUtil.getParentOfType(prevElement, PsiNewExpression.class) != null;
  }

  private static void shortenReference(PsiElement element) throws IncorrectOperationException {
    PsiDocMethodOrFieldRef javadocRef = PsiTreeUtil.getParentOfType(element, PsiDocMethodOrFieldRef.class);
    if (javadocRef != null) {
      element = javadocRef;
    }
    else {
      while (element.getParent() instanceof PsiJavaCodeReferenceElement) {
        element = element.getParent();
      }
    }
    JavaCodeStyleManager codeStyleManagerEx = JavaCodeStyleManager.getInstance(element.getProject());
    codeStyleManagerEx.shortenClassReferences(element, JavaCodeStyleManager.INCOMPLETE_CODE);
  }

  public static boolean hasQualifiedName(@NotNull String qName, @NotNull PsiMethod member) {
    String memberName = getMethodOrFieldQualifiedName(member);
    if (memberName == null || !qName.startsWith(memberName + "(")) return false;
    return qName.equals(memberName + getParameterString(member, false)) ||
           qName.equals(memberName + getParameterString(member, true));
  }
}