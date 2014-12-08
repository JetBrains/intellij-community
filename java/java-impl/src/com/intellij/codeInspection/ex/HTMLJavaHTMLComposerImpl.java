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

/*
 * User: anna
 * Date: 21-Dec-2007
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.HTMLComposer;
import com.intellij.codeInspection.HTMLJavaHTMLComposer;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.reference.*;
import com.intellij.psi.*;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HTMLJavaHTMLComposerImpl extends HTMLJavaHTMLComposer {
  private final HTMLComposerImpl myComposer;

  public HTMLJavaHTMLComposerImpl(final HTMLComposerImpl composer) {
    myComposer = composer;
  }

  @Override
  public void appendClassOrInterface(StringBuffer buf, RefClass refClass, boolean capitalizeFirstLetter) {
    if (refClass.isInterface()) {
      buf.append(capitalizeFirstLetter
                 ? InspectionsBundle.message("inspection.export.results.capitalized.interface")
                 : InspectionsBundle.message("inspection.export.results.interface"));
    }
    else if (refClass.isAbstract()) {
      buf.append(capitalizeFirstLetter
                 ? InspectionsBundle.message("inspection.export.results.capitalized.abstract.class")
                 : InspectionsBundle.message("inspection.export.results.abstract.class"));
    }
    else {
      buf.append(capitalizeFirstLetter
                 ? InspectionsBundle.message("inspection.export.results.capitalized.class")
                 : InspectionsBundle.message("inspection.export.results.class"));
    }
  }

  @Override
  public void appendClassExtendsImplements(StringBuffer buf, RefClass refClass) {
    if (refClass.getBaseClasses().size() > 0) {
      HTMLComposerImpl.appendHeading(buf, InspectionsBundle.message("inspection.export.results.extends.implements"));
      myComposer.startList(buf);
      for (RefClass refBase : refClass.getBaseClasses()) {
        myComposer.appendListItem(buf, refBase);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendDerivedClasses(StringBuffer buf, RefClass refClass) {
    if (refClass.getSubClasses().size() > 0) {
      if (refClass.isInterface()) {
        HTMLComposerImpl.appendHeading(buf, InspectionsBundle.message("inspection.export.results.extended.implemented"));
      }
      else {
        HTMLComposerImpl.appendHeading(buf, InspectionsBundle.message("inspection.export.results.extended"));
      }

      myComposer.startList(buf);
      for (RefClass refDerived : refClass.getSubClasses()) {
        myComposer.appendListItem(buf, refDerived);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendLibraryMethods(StringBuffer buf, RefClass refClass) {
    if (refClass.getLibraryMethods().size() > 0) {
      HTMLComposerImpl.appendHeading(buf, InspectionsBundle.message("inspection.export.results.overrides.library.methods"));

      myComposer.startList(buf);
      for (RefMethod refMethod : refClass.getLibraryMethods()) {
        myComposer.appendListItem(buf, refMethod);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendSuperMethods(StringBuffer buf, RefMethod refMethod) {
    if (refMethod.getSuperMethods().size() > 0) {
      HTMLComposer.appendHeading(buf, InspectionsBundle.message("inspection.export.results.overrides.implements"));

      myComposer.startList(buf);
      for (RefMethod refSuper : refMethod.getSuperMethods()) {
        myComposer.appendListItem(buf, refSuper);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendDerivedMethods(StringBuffer buf, RefMethod refMethod) {
    if (refMethod.getDerivedMethods().size() > 0) {
      HTMLComposer.appendHeading(buf, InspectionsBundle.message("inspection.export.results.derived.methods"));

      myComposer.startList(buf);
      for (RefMethod refDerived : refMethod.getDerivedMethods()) {
        myComposer.appendListItem(buf, refDerived);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendTypeReferences(StringBuffer buf, RefClass refClass) {
    if (refClass.getInTypeReferences().size() > 0) {
      HTMLComposer.appendHeading(buf, InspectionsBundle.message("inspection.export.results.type.references"));

      myComposer.startList(buf);
      for (final RefElement refElement : refClass.getInTypeReferences()) {
        myComposer.appendListItem(buf, refElement);
      }
      myComposer.doneList(buf);
    }
  }

  @Override
  public void appendShortName(final RefEntity refElement, final StringBuffer buf) {
    if (refElement instanceof RefJavaElement) {
      String modifier = ((RefJavaElement)refElement).getAccessModifier();
      if (modifier != null && modifier != PsiModifier.PACKAGE_LOCAL) {
        buf.append(modifier);
        buf.append(HTMLComposerImpl.NBSP);
      }
    }
    refElement.accept(new RefJavaVisitor() {
      @Override
      public void visitClass(@NotNull RefClass refClass) {
        if (refClass.isStatic()) {
          buf.append(InspectionsBundle.message("inspection.export.results.static"));
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
        PsiField psiField = field.getElement();
        if (psiField != null) {
          if (field.isStatic()) {
            buf.append(InspectionsBundle.message("inspection.export.results.static"));
            buf.append(HTMLComposerImpl.NBSP);
          }

          buf.append(InspectionsBundle.message("inspection.export.results.field"));
          buf.append(HTMLComposerImpl.NBSP).append(HTMLComposerImpl.CODE_OPENING);

          buf.append(XmlStringUtil.escapeString(psiField.getType().getPresentableText()));
          buf.append(HTMLComposerImpl.NBSP).append(HTMLComposerImpl.B_OPENING);
          buf.append(psiField.getName());
          buf.append(HTMLComposerImpl.B_CLOSING).append(HTMLComposerImpl.CODE_CLOSING);
        }
      }

      @Override
      public void visitMethod(@NotNull RefMethod method) {
        PsiMethod psiMethod = (PsiMethod)method.getElement();
        if (psiMethod != null) {
          PsiType returnType = psiMethod.getReturnType();

          if (method.isStatic()) {
            buf.append(InspectionsBundle.message("inspection.export.results.static"));
            buf.append(HTMLComposerImpl.NBSP);
          }
          else if (method.isAbstract()) {
            buf.append(InspectionsBundle.message("inspection.export.results.abstract"));
            buf.append(HTMLComposerImpl.NBSP);
          }
          buf.append(method.isConstructor()
                     ? InspectionsBundle.message("inspection.export.results.constructor")
                     : InspectionsBundle.message("inspection.export.results.method"));
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
      }

      @Override
      public void visitFile(@NotNull RefFile file) {
        final PsiFile psiFile = file.getElement();
        buf.append(HTMLComposerImpl.B_OPENING);
        buf.append(psiFile.getName());
        buf.append(HTMLComposerImpl.B_CLOSING);
      }
    });
  }

  @Override
  public void appendLocation(final RefEntity entity, final StringBuffer buf) {
    RefEntity owner = entity.getOwner();
    if (owner instanceof RefPackage) {
      buf.append(InspectionsBundle.message("inspection.export.results.package"));
      buf.append(HTMLComposerImpl.NBSP).append(HTMLComposerImpl.CODE_OPENING);
      buf.append(RefJavaUtil.getInstance().getPackageName(entity));
      buf.append(HTMLComposerImpl.CODE_CLOSING);
    }
    else if (owner instanceof RefMethod) {
      buf.append(InspectionsBundle.message("inspection.export.results.method"));
      buf.append(HTMLComposerImpl.NBSP);
      myComposer.appendElementReference(buf, (RefElement)owner);
    }
    else if (owner instanceof RefField) {
      buf.append(InspectionsBundle.message("inspection.export.results.field"));
      buf.append(HTMLComposerImpl.NBSP);
      myComposer.appendElementReference(buf, (RefElement)owner);
      buf.append(HTMLComposerImpl.NBSP);
      buf.append(InspectionsBundle.message("inspection.export.results.initializer"));
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
    else if (refEntity instanceof RefMethod) {
      PsiMethod psiMethod = (PsiMethod)((RefMethod)refEntity).getElement();
      if (psiMethod != null) {
        return psiMethod.getName();
      }
      else {
        return refEntity.getName();
      }
    }
    return null;
  }

  @Override
  public void appendReferencePresentation(RefEntity refElement, final StringBuffer buf, final boolean isPackageIncluded) {
    if (refElement instanceof RefImplicitConstructor) {
      buf.append(InspectionsBundle.message("inspection.export.results.implicit.constructor"));
      refElement = ((RefImplicitConstructor)refElement).getOwnerClass();
    }

    buf.append(HTMLComposerImpl.CODE_OPENING);

    if (refElement instanceof RefField) {
      RefField field = (RefField)refElement;
      PsiField psiField = field.getElement();
      buf.append(XmlStringUtil.escapeString(psiField.getType().getPresentableText()));
      buf.append(HTMLComposerImpl.NBSP);
    }
    else if (refElement instanceof RefMethod) {
      RefMethod method = (RefMethod)refElement;
      PsiMethod psiMethod = (PsiMethod)method.getElement();
      PsiType returnType = psiMethod.getReturnType();

      if (returnType != null) {
        buf.append(XmlStringUtil.escapeString(returnType.getPresentableText()));
        buf.append(HTMLComposerImpl.NBSP);
      }
    }

    buf.append(HTMLComposerImpl.A_HREF_OPENING);

    if (myComposer.myExporter == null) {
      buf.append(((RefElementImpl)refElement).getURL());
    }
    else {
      buf.append(myComposer.myExporter.getURL(refElement));
    }

    buf.append("\">");

    if (refElement instanceof RefClass && ((RefClass)refElement).isAnonymous()) {
      buf.append(InspectionsBundle.message("inspection.reference.anonymous"));
    }
    else if (refElement instanceof RefJavaElement && ((RefJavaElement)refElement).isSyntheticJSP()) {
      buf.append(XmlStringUtil.escapeString(refElement.getName()));
    }
    else if (refElement instanceof RefMethod) {
      PsiMethod psiMethod = (PsiMethod)((RefMethod)refElement).getElement();
      buf.append(psiMethod.getName());
    }
    else {
      buf.append(refElement.getName());
    }

    buf.append(HTMLComposerImpl.A_CLOSING);

    if (refElement instanceof RefMethod) {
      PsiMethod psiMethod = (PsiMethod)((RefMethod)refElement).getElement();
      appendMethodParameters(buf, psiMethod, false);
    }

    buf.append(HTMLComposerImpl.CODE_CLOSING);

    if (refElement instanceof RefClass && ((RefClass)refElement).isAnonymous()) {
      buf.append(" ");
      buf.append(InspectionsBundle.message("inspection.export.results.anonymous.ref.in.owner"));
      buf.append(" ");
      myComposer.appendElementReference(buf, ((RefElement)refElement.getOwner()), isPackageIncluded);
    }
    else if (isPackageIncluded) {
      buf.append(" ").append(HTMLComposerImpl.CODE_OPENING).append("(");
      myComposer.appendQualifiedName(buf, refElement.getOwner());
//      buf.append(RefUtil.getPackageName(refElement));
      buf.append(")").append(HTMLComposerImpl.CODE_CLOSING);
    }
  }

  private static void appendMethodParameters(StringBuffer buf, PsiMethod method, boolean showNames) {
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
