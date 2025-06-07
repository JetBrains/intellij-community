/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.memory.InnerClassReferenceVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class ClassMayBeInterfaceInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean reportClassesWithNonAbstractMethods = false;

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("class.may.be.interface.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("reportClassesWithNonAbstractMethods", InspectionGadgetsBundle.message("class.may.be.interface.java8.option")));
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (reportClassesWithNonAbstractMethods) {
      node.addContent(new Element("option").setAttribute("name", "reportClassesWithNonAbstractMethods").setAttribute("value", "true"));
    }
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ClassMayBeInterfaceFix();
  }

  private static class ClassMayBeInterfaceFix extends ModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("class.may.be.interface.convert.quickfix");
    }

    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiIdentifier classNameIdentifier = (PsiIdentifier)descriptor.getPsiElement();
      final PsiClass interfaceClass = (PsiClass)classNameIdentifier.getParent();
      final SearchScope searchScope = interfaceClass.getUseScope();
      final List<PsiClass> elements = new ArrayList<>();
      elements.add(interfaceClass);
      for (final PsiClass inheritor : ClassInheritorsSearch.search(interfaceClass, searchScope, false).asIterable()) {
        elements.add(inheritor);
      }
      return ModCommand.psiUpdate(interfaceClass, (cls, updater) -> {
        moveSubClassExtendsToImplements(ContainerUtil.map(elements, updater::getWritable));
        changeClassToInterface(cls);
        moveImplementsToExtends(cls);
      });
    }

    private static void changeClassToInterface(PsiClass aClass) {
      for (PsiMethod method : aClass.getMethods()) {
        if (isEmptyConstructor(method)) {
          method.delete();
          continue;
        }
        PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, false);
        if (method.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        else if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
          PsiUtil.setModifierProperty(method, PsiModifier.ABSTRACT, false); // redundant modifier
          continue;
        }
        PsiUtil.setModifierProperty(method, PsiModifier.DEFAULT, true);
      }
      for (PsiField field : aClass.getFields()) {
        PsiUtil.setModifierProperty(field, PsiModifier.PUBLIC, false);
        PsiUtil.setModifierProperty(field, PsiModifier.STATIC, false);
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, false);
      }
      for (PsiClass innerClass : aClass.getInnerClasses()) {
        PsiUtil.setModifierProperty(innerClass, PsiModifier.PUBLIC, false);
      }
      final PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
      if (nameIdentifier == null) {
        return;
      }
      final PsiKeyword classKeyword = PsiTreeUtil.getPrevSiblingOfType(nameIdentifier, PsiKeyword.class);
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(aClass.getProject());
      final PsiKeyword interfaceKeyword = factory.createKeyword(JavaKeywords.INTERFACE);
      if (classKeyword == null) {
        return;
      }
      PsiUtil.setModifierProperty(aClass, PsiModifier.ABSTRACT, false);
      PsiUtil.setModifierProperty(aClass, PsiModifier.FINAL, false);
      classKeyword.replace(interfaceKeyword);
    }

    private static void moveImplementsToExtends(PsiClass anInterface) {
      final PsiReferenceList extendsList = anInterface.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final PsiReferenceList implementsList = anInterface.getImplementsList();
      if (implementsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] referenceElements = implementsList.getReferenceElements();
      for (final PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        extendsList.add(referenceElement);
        referenceElement.delete();
      }
    }

    private static void moveSubClassExtendsToImplements(List<PsiClass> inheritors) {
      final PsiClass oldClass = inheritors.get(0);
      for (int i = 1; i < inheritors.size(); i++) {
        final PsiClass inheritor = inheritors.get(i);
        final PsiReferenceList extendsList = inheritor.getExtendsList();
        if (extendsList == null) {
          continue;
        }
        final PsiReferenceList implementsList = inheritor.getImplementsList();
        moveReference(extendsList, implementsList, oldClass);
      }
    }

    private static void moveReference(@NotNull PsiReferenceList source, @Nullable PsiReferenceList target,
                                      @NotNull PsiClass oldClass) {
      final PsiJavaCodeReferenceElement[] sourceReferences = source.getReferenceElements();
      for (final PsiJavaCodeReferenceElement sourceReference : sourceReferences) {
        if (sourceReference.isReferenceTo(oldClass)) {
          if (target != null) {
            target.add(sourceReference);
          }
          sourceReference.delete();
        }
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassMayBeInterfaceVisitor();
  }

  static boolean isEmptyConstructor(@NotNull PsiMethod method) {
    return method.isConstructor() && MethodUtils.isTrivial(method);
  }

  private class ClassMayBeInterfaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter || aClass instanceof PsiAnonymousClass) {
        return;
      }
      if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (PsiUtil.isLocalClass(aClass) && !PsiUtil.isAvailable(JavaFeature.LOCAL_INTERFACES, aClass)) {
        return;
      }
      if (!mayBeInterface(aClass)) {
        return;
      }
      if (ClassUtils.isInnerClass(aClass)) {
        final InnerClassReferenceVisitor visitor = new InnerClassReferenceVisitor(aClass);
        aClass.accept(visitor);
        if (!visitor.canInnerClassBeStatic()) {
          return;
        }
      }
      registerClassError(aClass);
    }

    public boolean mayBeInterface(PsiClass aClass) {
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList != null) {
        final PsiJavaCodeReferenceElement[] extendsElements = extendsList.getReferenceElements();
        if (extendsElements.length > 0) {
          return false;
        }
      }
      final PsiClassInitializer[] initializers = aClass.getInitializers();
      if (initializers.length > 0) {
        return false;
      }
      return allMethodsPublicAbstract(aClass) && allFieldsPublicStaticFinal(aClass) && allInnerClassesPublic(aClass);
    }

    private static boolean allFieldsPublicStaticFinal(PsiClass aClass) {
      boolean allFieldsStaticFinal = true;
      final PsiField[] fields = aClass.getFields();
      for (final PsiField field : fields) {
        if (!(field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)
              && field.hasModifierProperty(PsiModifier.PUBLIC))) {
          allFieldsStaticFinal = false;
        }
      }
      return allFieldsStaticFinal;
    }

    private boolean allMethodsPublicAbstract(PsiClass aClass) {
      final PsiMethod[] methods = aClass.getMethods();
      for (final PsiMethod method : methods) {
        if (isEmptyConstructor(method)) {
          continue;
        }
        if (!method.hasModifierProperty(PsiModifier.ABSTRACT)) {
          if (MethodUtils.isToString(method) || MethodUtils.isHashCode(method) || MethodUtils.isEquals(method)) {
            // can't have default methods overriding Object methods.
            return false;
          }
          if (!reportClassesWithNonAbstractMethods || !PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, aClass)) {
            return false;
          }
        }
        if (!method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.FINAL)) {
          return false;
        }
      }
      return true;
    }

    private static boolean allInnerClassesPublic(PsiClass aClass) {
      final PsiClass[] innerClasses = aClass.getInnerClasses();
      for (PsiClass innerClass : innerClasses) {
        if (!innerClass.hasModifierProperty(PsiModifier.PUBLIC)) {
          return false;
        }
      }
      return true;
    }
  }
}
