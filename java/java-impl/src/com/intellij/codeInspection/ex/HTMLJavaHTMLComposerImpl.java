// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInspection.HTMLJavaHTMLComposer;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefElementImpl;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefField;
import com.intellij.codeInspection.reference.RefFile;
import com.intellij.codeInspection.reference.RefFunctionalExpression;
import com.intellij.codeInspection.reference.RefImplicitConstructor;
import com.intellij.codeInspection.reference.RefJavaElement;
import com.intellij.codeInspection.reference.RefJavaUtil;
import com.intellij.codeInspection.reference.RefJavaVisitor;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.codeInspection.reference.RefParameter;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UMethod;

import java.util.List;

public class HTMLJavaHTMLComposerImpl extends HTMLJavaHTMLComposer {
  private final HTMLComposerImpl myComposer;

  public HTMLJavaHTMLComposerImpl(final HTMLComposerImpl composer) {
    myComposer = composer;
  }

  @Override
  public void appendClassOrInterface(@NotNull StringBuilder buf, @NotNull RefClass refClass, boolean capitalizeFirstLetter) {
    String message;
    if (refClass.isAnnotationType()) {
      message = AnalysisBundle.message("inspection.export.results.annotation.type");
    }
    else if (refClass.isInterface()) {
      message = AnalysisBundle.message("inspection.export.results.capitalized.interface");
    }
    else if (refClass.isAbstract()) {
      message = AnalysisBundle.message("inspection.export.results.capitalized.abstract.class");
    }
    else if (refClass.isEnum()) {
      message = AnalysisBundle.message("inspection.export.results.enum.class");
    }
    else if (refClass.isRecord()) {
      message = AnalysisBundle.message("inspection.export.results.record.class");
    }
    else {
      message = AnalysisBundle.message("inspection.export.results.capitalized.class");
    }
    buf.append(capitalizeFirstLetter ? Strings.capitalize(message) : message);
  }

  @Override
  public void appendClassExtendsImplements(@NotNull StringBuilder buf, @NotNull RefClass refClass) {
    myComposer.appendSection(buf, AnalysisBundle.message("inspection.export.results.extends.implements"), refClass.getBaseClasses());
  }

  @Override
  public void appendDerivedClasses(@NotNull StringBuilder buf, @NotNull RefClass refClass) {
    String header = refClass.isInterface()
                    ? AnalysisBundle.message("inspection.export.results.extended.implemented")
                    : AnalysisBundle.message("inspection.export.results.extended");
    myComposer.appendSection(buf, header, refClass.getSubClasses());
  }

  @Override
  public void appendLibraryMethods(@NotNull StringBuilder buf, @NotNull RefClass refClass) {
    myComposer.appendSection(buf, JavaBundle.message("inspection.export.results.overrides.library.methods"), refClass.getLibraryMethods());
  }

  @Override
  public void appendSuperMethods(@NotNull StringBuilder buf, @NotNull RefMethod refMethod) {
    myComposer.appendSection(buf, AnalysisBundle.message("inspection.export.results.overrides.implements"), refMethod.getSuperMethods());
  }

  @Override
  public void appendDerivedMethods(@NotNull StringBuilder buf, @NotNull RefMethod refMethod) {
    myComposer.appendSection(buf, AnalysisBundle.message("inspection.export.results.derived.methods"), refMethod.getDerivedMethods());
  }

  @Override
  public void appendDerivedFunctionalExpressions(@NotNull StringBuilder buf, @NotNull RefMethod refMethod) {
    List<RefFunctionalExpression> functionalExpressions =
      ContainerUtil.filterIsInstance(refMethod.getDerivedReferences(), RefFunctionalExpression.class);
    myComposer.appendSection(buf, AnalysisBundle.message("inspection.export.results.derived.functional.expressions"), functionalExpressions);
  }

  @Override
  public void appendTypeReferences(@NotNull StringBuilder buf, @NotNull RefClass refClass) {
    myComposer.appendSection(buf, JavaBundle.message("inspection.export.results.type.references"), refClass.getInTypeReferences());
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
      String name = RefJavaUtil.getInstance().getPackageName(entity);
      assert name != null;
      // name for default package: <default>
      buf.append(XmlStringUtil.escapeString(name));
      buf.append(HTMLComposerImpl.CODE_CLOSING);
    }
    else if (owner instanceof RefMethod) {
      myComposer.appendElementReference(buf, (RefElement)owner, false);
    }
    else if (owner instanceof RefField) {
      myComposer.appendElementReference(buf, (RefElement)owner, false);
      buf.append(HTMLComposerImpl.NBSP);
      buf.append(AnalysisBundle.message("inspection.export.results.initializer"));
    }
    else if (owner instanceof RefClass) {
      myComposer.appendElementReference(buf, (RefElement)owner, false);
    }
  }

  @Override
  public @Nullable String getQualifiedName(final RefEntity refEntity) {
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
  public void appendReferencePresentation(RefEntity refElement, @NotNull StringBuilder buf, boolean capitalize) {
    String message;
    if (refElement instanceof RefImplicitConstructor) {
      message = JavaBundle.message("inspection.export.results.implicit.constructor");
      refElement = ((RefImplicitConstructor)refElement).getOwnerClass();
    }
    else if (refElement instanceof RefParameter) {
      message = AnalysisBundle.message("inspection.export.results.parameter");
    }
    else if (refElement instanceof RefField field) {
      message = field.isEnumConstant() 
                ? AnalysisBundle.message("inspection.export.results.enum.constant")
                : AnalysisBundle.message("inspection.export.results.field");
    }
    else if (refElement instanceof RefMethod method) {
      message = AnalysisBundle.message(
        method.isConstructor() ? "inspection.export.results.constructor" : "inspection.export.results.method");
    }
    else if (refElement instanceof RefClass refClass) {
      if (refClass.isInterface()) {
        message = refClass.isAnnotationType()
                  ? AnalysisBundle.message("inspection.export.results.annotation.type")
                  : AnalysisBundle.message("inspection.export.results.interface");
      }
      else if (refClass.isEnum()) {
        message = AnalysisBundle.message("inspection.export.results.enum.class");
      }
      else if (refClass.isRecord()) {
        message = AnalysisBundle.message("inspection.export.results.record.class");
      }
      else if (refClass.isAbstract()) {
        message = AnalysisBundle.message("inspection.export.results.abstract.class");
      }
      else {
        message = AnalysisBundle.message("inspection.export.results.class");
      }
    }
    else if (refElement instanceof RefFunctionalExpression functionalExpression) {
      message = functionalExpression.isMethodReference()
                ? AnalysisBundle.message("inspection.export.results.method.reference")
                : AnalysisBundle.message("inspection.export.results.lambda.expression");
    }
    else {
      message = "";
    }
    buf.append(capitalize ? Strings.capitalize(message) : message).append(HTMLComposerImpl.NBSP);
    buf.append(HTMLComposerImpl.CODE_OPENING);
    buf.append(HTMLComposerImpl.A_HREF_OPENING);
    buf.append(((RefElementImpl)refElement).getURL());
    if (refElement instanceof RefClass) {
      buf.append("\" qualifiedname=\"").append(XmlStringUtil.escapeString(refElement.getQualifiedName()));
    }
    buf.append("\">");

    if (refElement instanceof RefClass refClass && refClass.isAnonymous() ||
        refElement instanceof RefFunctionalExpression) {
      buf.append(XmlStringUtil.escapeString(AnalysisBundle.message("inspection.reference.anonymous")));
    }
    else if (refElement instanceof RefJavaElement && ((RefJavaElement)refElement).isSyntheticJSP()) {
      buf.append(XmlStringUtil.escapeString(refElement.getName()));
    }
    else if (refElement instanceof RefMethod refMethod) {
      UMethod uMethod = refMethod.getUastElement();
      buf.append(uMethod.getName());
      appendMethodParameters(buf, uMethod.getJavaPsi(), false);
    }
    else {
      buf.append(refElement.getName());
    }

    buf.append(HTMLComposerImpl.A_CLOSING);
    buf.append(HTMLComposerImpl.CODE_CLOSING);

    final RefEntity owner = refElement.getOwner();
    if (owner != null) {
      if (refElement instanceof RefFunctionalExpression ||
          refElement instanceof RefParameter ||
          (refElement instanceof RefClass refClass && refClass.isAnonymous())) {
        buf.append(" ").append(AnalysisBundle.message("inspection.export.results.anonymous.ref.in.owner")).append(" ");
        myComposer.appendElementReference(buf, (RefElement) owner, false);
      }
      else {
        buf.append(" ").append(AnalysisBundle.message("inspection.export.results.anonymous.ref.in.owner")).append(" ");
        buf.append(HTMLComposerImpl.CODE_OPENING);
        if (owner instanceof RefElementImpl) {
          buf.append(HTMLComposerImpl.A_HREF_OPENING);
          buf.append(((RefElementImpl)owner).getURL());
          buf.append("\" qualifiedname=\"").append(XmlStringUtil.escapeString(owner.getQualifiedName()));
          buf.append("\">");
        }
        buf.append(owner.getName());
        if (owner instanceof RefElementImpl) {
          buf.append(HTMLComposerImpl.A_CLOSING);
        }
        buf.append(HTMLComposerImpl.CODE_CLOSING);
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
        buf.append(' ').append(param.getName());
      }
    }
    buf.append(')');
  }
}
