// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInspection.HTMLComposer;
import com.intellij.codeInspection.HTMLJavaHTMLComposer;
import com.intellij.codeInspection.reference.*;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.List;

public class HTMLJavaHTMLComposerImpl extends HTMLJavaHTMLComposer {
  private final HTMLComposerImpl myComposer;

  public HTMLJavaHTMLComposerImpl(final HTMLComposerImpl composer) {
    myComposer = composer;
  }

  @Override
  public void appendClassOrInterface(@NotNull StringBuilder buf, @NotNull RefClass refClass, boolean capitalizeFirstLetter) {
    if (refClass.isInterface()) {
      buf.append(capitalizeFirstLetter
                 ? AnalysisBundle.message("inspection.export.results.capitalized.interface")
                 : AnalysisBundle.message("inspection.export.results.interface"));
    }
    else if (refClass.isAbstract()) {
      buf.append(capitalizeFirstLetter
                 ? AnalysisBundle.message("inspection.export.results.capitalized.abstract.class")
                 : AnalysisBundle.message("inspection.export.results.abstract.class"));
    }
    else {
      buf.append(capitalizeFirstLetter
                 ? AnalysisBundle.message("inspection.export.results.capitalized.class")
                 : AnalysisBundle.message("inspection.export.results.class"));
    }
  }

  @Override
  public void appendClassExtendsImplements(@NotNull StringBuilder buf, @NotNull RefClass refClass) {
    if (refClass.getBaseClasses().size() > 0) {
      HTMLComposer.appendHeading(buf, AnalysisBundle.message("inspection.export.results.extends.implements"));
      myComposer.startList(buf);
      for (RefClass refBase : refClass.getBaseClasses()) {
        myComposer.appendListItem(buf, refBase);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendDerivedClasses(@NotNull StringBuilder buf, @NotNull RefClass refClass) {
    if (refClass.getSubClasses().size() > 0) {
      if (refClass.isInterface()) {
        HTMLComposer.appendHeading(buf, AnalysisBundle.message("inspection.export.results.extended.implemented"));
      }
      else {
        HTMLComposer.appendHeading(buf, AnalysisBundle.message("inspection.export.results.extended"));
      }

      myComposer.startList(buf);
      for (RefClass refDerived : refClass.getSubClasses()) {
        myComposer.appendListItem(buf, refDerived);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendLibraryMethods(@NotNull StringBuilder buf, @NotNull RefClass refClass) {
    if (refClass.getLibraryMethods().size() > 0) {
      HTMLComposer.appendHeading(buf, JavaBundle.message("inspection.export.results.overrides.library.methods"));

      myComposer.startList(buf);
      for (RefMethod refMethod : refClass.getLibraryMethods()) {
        myComposer.appendListItem(buf, refMethod);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendSuperMethods(@NotNull StringBuilder buf, @NotNull RefMethod refMethod) {
    if (refMethod.getSuperMethods().size() > 0) {
      HTMLComposer.appendHeading(buf, AnalysisBundle.message("inspection.export.results.overrides.implements"));

      myComposer.startList(buf);
      for (RefMethod refSuper : refMethod.getSuperMethods()) {
        myComposer.appendListItem(buf, refSuper);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendDerivedMethods(@NotNull StringBuilder buf, @NotNull RefMethod refMethod) {
    if (refMethod.getDerivedMethods().size() > 0) {
      HTMLComposer.appendHeading(buf, AnalysisBundle.message("inspection.export.results.derived.methods"));

      myComposer.startList(buf);
      for (RefMethod refDerived : refMethod.getDerivedMethods()) {
        myComposer.appendListItem(buf, refDerived);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendDerivedFunctionalExpressions(@NotNull StringBuilder buf, @NotNull RefMethod refMethod) {
    List<RefFunctionalExpression> functionalExpressions =
      ContainerUtil.filterIsInstance(refMethod.getDerivedReferences(), RefFunctionalExpression.class);
    if (!functionalExpressions.isEmpty()) {
      HTMLComposer.appendHeading(buf, "Derived lambdas and method references");

      myComposer.startList(buf);
      for (RefFunctionalExpression functionalExpression : functionalExpressions) {
        myComposer.appendListItem(buf, functionalExpression);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendTypeReferences(@NotNull StringBuilder buf, @NotNull RefClass refClass) {
    if (refClass.getInTypeReferences().size() > 0) {
      HTMLComposer.appendHeading(buf, JavaBundle.message("inspection.export.results.type.references"));

      myComposer.startList(buf);
      for (final RefElement refElement : refClass.getInTypeReferences()) {
        myComposer.appendListItem(buf, refElement);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendShortName(final RefEntity refElement, @NotNull StringBuilder buf) {
    if (refElement instanceof RefJavaElement) {
      String modifier = ((RefJavaElement)refElement).getAccessModifier();
      if (!modifier.equals(PsiModifier.PACKAGE_LOCAL)) {
        buf.append(modifier);
        buf.append(HTMLComposerImpl.NBSP);
      }
    }
    refElement.accept(new RefJavaVisitor() {
      @Override
      public void visitClass(@NotNull RefClass refClass) {
        if (refClass.isStatic()) {
          buf.append(AnalysisBundle.message("inspection.export.results.static"));
          buf.append(HTMLComposerImpl.NBSP);
        }

        appendClassOrInterface(buf, refClass, false);
        buf.append(HTMLComposerImpl.NBSP).append(HTMLComposerImpl.B_OPENING).append(HTMLComposerImpl.CODE_OPENING);
        final String name = refClass.getName();
        buf.append(refClass.isSyntheticJSP() ? XmlStringUtil.escapeString(name) : name);
        buf.append(HTMLComposerImpl.CODE_CLOSING).append(HTMLComposerImpl.B_CLOSING);
      }

      @Override
      public void visitField(@NotNull RefField field) {
        PsiField psiField = (PsiField)field.getUastElement().getJavaPsi();
        if (psiField != null) {
          if (field.isStatic()) {
            buf.append(AnalysisBundle.message("inspection.export.results.static"));
            buf.append(HTMLComposerImpl.NBSP);
          }

          buf.append(AnalysisBundle.message("inspection.export.results.field"));
          buf.append(HTMLComposerImpl.NBSP).append(HTMLComposerImpl.CODE_OPENING);

          buf.append(XmlStringUtil.escapeString(psiField.getType().getPresentableText()));
          buf.append(HTMLComposerImpl.NBSP).append(HTMLComposerImpl.B_OPENING);
          buf.append(psiField.getName());
          buf.append(HTMLComposerImpl.B_CLOSING).append(HTMLComposerImpl.CODE_CLOSING);
        }
      }

      @Override
      public void visitMethod(@NotNull RefMethod method) {
        UMethod uMethod = method.getUastElement();
        if (uMethod == null) return;
        PsiMethod psiMethod = uMethod.getJavaPsi();
        PsiType returnType = psiMethod.getReturnType();

        if (method.isStatic()) {
          buf.append(AnalysisBundle.message("inspection.export.results.static"));
          buf.append(HTMLComposerImpl.NBSP);
        }
        else if (method.isAbstract()) {
          buf.append(AnalysisBundle.message("inspection.export.results.abstract"));
          buf.append(HTMLComposerImpl.NBSP);
        }
        buf.append(method.isConstructor()
                   ? AnalysisBundle.message("inspection.export.results.constructor")
                   : AnalysisBundle.message("inspection.export.results.method"));
        buf.append(HTMLComposerImpl.NBSP).append(HTMLComposerImpl.CODE_OPENING);

        if (returnType != null) {
          buf.append(XmlStringUtil.escapeString(returnType.getPresentableText()));
          buf.append(HTMLComposerImpl.NBSP);
        }

        buf.append(HTMLComposerImpl.B_OPENING);
        buf.append(psiMethod.getName());
        buf.append(HTMLComposerImpl.B_CLOSING);
        appendMethodParameters(buf, psiMethod, true);
        buf.append(HTMLComposerImpl.CODE_CLOSING);
      }

      @Override
      public void visitFile(@NotNull RefFile file) {
        final PsiFile psiFile = file.getPsiElement();
        buf.append(HTMLComposerImpl.B_OPENING);
        buf.append(psiFile.getName());
        buf.append(HTMLComposerImpl.B_CLOSING);
      }
    });
  }

  @Override
  public void appendLocation(final RefEntity entity, @NotNull StringBuilder buf) {
    RefEntity owner = entity.getOwner();
    if (owner instanceof RefPackage) {
      buf.append(JavaBundle.message("inspection.export.results.package"));
      buf.append(HTMLComposerImpl.NBSP).append(HTMLComposerImpl.CODE_OPENING);
      buf.append(RefJavaUtil.getInstance().getPackageName(entity));
      buf.append(HTMLComposerImpl.CODE_CLOSING);
    }
    else if (owner instanceof RefMethod) {
      buf.append(AnalysisBundle.message("inspection.export.results.method"));
      buf.append(HTMLComposerImpl.NBSP);
      myComposer.appendElementReference(buf, (RefElement)owner);
    }
    else if (owner instanceof RefField) {
      buf.append(AnalysisBundle.message("inspection.export.results.field"));
      buf.append(HTMLComposerImpl.NBSP);
      myComposer.appendElementReference(buf, (RefElement)owner);
      buf.append(HTMLComposerImpl.NBSP);
      buf.append(AnalysisBundle.message("inspection.export.results.initializer"));
    }
    else if (owner instanceof RefClass) {
      appendClassOrInterface(buf, (RefClass)owner, false);
      buf.append(HTMLComposerImpl.NBSP);
      myComposer.appendElementReference(buf, (RefElement)owner);
    }
  }

  @Override
  @Nullable
  public String getQualifiedName(final RefEntity refEntity) {
    if (refEntity instanceof RefJavaElement && ((RefJavaElement)refEntity).isSyntheticJSP()) {
      return XmlStringUtil.escapeString(refEntity.getName());
    }
    else if (refEntity instanceof RefMethod refMethod) {
      UMethod uMethod = refMethod.getUastElement();
      if (uMethod != null) {
        return uMethod.getName();
      }
      else {
        return refEntity.getName();
      }
    }
    return null;
  }

  @Override
  public void appendReferencePresentation(RefEntity refElement, @NotNull StringBuilder buf, final boolean isPackageIncluded) {
    if (refElement instanceof RefImplicitConstructor) {
      buf.append(JavaBundle.message("inspection.export.results.implicit.constructor"));
      buf.append("&nbsp;");
      refElement = ((RefImplicitConstructor)refElement).getOwnerClass();
    }

    buf.append(HTMLComposerImpl.CODE_OPENING);

    if (refElement instanceof RefField field) {
      UField psiField = field.getUastElement();
      buf.append(XmlStringUtil.escapeString(psiField.getType().getPresentableText()));
      buf.append(HTMLComposerImpl.NBSP);
    }
    else if (refElement instanceof RefMethod method) {
      UMethod psiMethod = method.getUastElement();
      PsiType returnType = psiMethod.getReturnType();

      if (returnType != null) {
        buf.append(XmlStringUtil.escapeString(returnType.getPresentableText()));
        buf.append(HTMLComposerImpl.NBSP);
      }
    }

    buf.append(HTMLComposerImpl.A_HREF_OPENING);

    buf.append(((RefElementImpl)refElement).getURL());

    buf.append("\"");

    if (isPackageIncluded) {
      buf.append(" qualifiedname=\"");
      buf.append(refElement.getQualifiedName());
      buf.append("\"");
    }

    buf.append(">");

    if (refElement instanceof RefClass && ((RefClass)refElement).isAnonymous()) {
      buf.append(AnalysisBundle.message("inspection.reference.anonymous"));
    }
    else if (refElement instanceof RefJavaElement && ((RefJavaElement)refElement).isSyntheticJSP()) {
      buf.append(XmlStringUtil.escapeString(refElement.getName()));
    }
    else if (refElement instanceof RefMethod) {
      UMethod psiMethod = ((RefMethod)refElement).getUastElement();
      buf.append(psiMethod.getName());
    }
    else if (refElement instanceof RefFunctionalExpression) {
      UExpression functionalExpr = ((RefFunctionalExpression)refElement).getUastElement();
      if (functionalExpr instanceof ULambdaExpression) {
        buf.append(refElement.getName());
      }
      else if (functionalExpr instanceof UCallableReferenceExpression) {
        buf.append(functionalExpr.asSourceString());
      }
    }
    else {
      buf.append(refElement.getName());
    }

    buf.append(HTMLComposerImpl.A_CLOSING);

    if (refElement instanceof RefMethod refMethod) {
      PsiMethod psiMethod = refMethod.getUastElement().getJavaPsi();
      if (psiMethod != null) {
        appendMethodParameters(buf, psiMethod, false);
      }
    }

    buf.append(HTMLComposerImpl.CODE_CLOSING);

    final RefEntity owner = refElement.getOwner();
    if (owner != null) {
      if ((refElement instanceof RefClass && ((RefClass)refElement).isAnonymous())) {
        buf.append(" ");
        buf.append(AnalysisBundle.message("inspection.export.results.anonymous.ref.in.owner"));
        buf.append(" ");
        myComposer.appendElementReference(buf, (RefElement) owner, isPackageIncluded);
      }
      else if (isPackageIncluded) {
        buf.append(" ").append("<code class=\"package\">").append("(");
        myComposer.appendQualifiedName(buf, owner);
        //      buf.append(RefUtil.getPackageName(refElement));
        buf.append(")").append(HTMLComposerImpl.CODE_CLOSING);
      }
    }
  }

  private static void appendMethodParameters(@NotNull StringBuilder buf, PsiMethod method, boolean showNames) {
    PsiParameter[] params = method.getParameterList().getParameters();
    buf.append('(');
    for (int i = 0; i < params.length; i++) {
      if (i != 0) buf.append(", ");
      PsiParameter param = params[i];
      buf.append(XmlStringUtil.escapeString(param.getType().getPresentableText()));
      if (showNames) {
        buf.append(' ');
        buf.append(param.getName());
      }
    }
    buf.append(')');
  }

}
