// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.codeInsight.ClassUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.java.codeserver.core.JavaPsiSingleFileSourceUtil;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKind;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.ImplicitClassSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNullElse;

final class ClassChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  ClassChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  /**
   * new ref(...) or new ref(...) { ... } where ref is an abstract class
   */
  void checkAbstractInstantiation(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiElement parent = ref.getParent();
    if (parent instanceof PsiAnonymousClass aClass
        && parent.getParent() instanceof PsiNewExpression
        && !PsiUtilCore.hasErrorElementChild(parent.getParent())) {
      checkClassWithAbstractMethods(aClass);
    }
  }

  private void checkClassWithAbstractMethods(@NotNull PsiClass aClass) {
    PsiMethod abstractMethod = ClassUtil.getAnyAbstractMethod(aClass);
    if (abstractMethod == null) return;

    PsiClass containingClass = abstractMethod.getContainingClass();
    if (containingClass == null || containingClass == aClass) return;

    myVisitor.report(JavaErrorKinds.CLASS_NO_ABSTRACT_METHOD.create(aClass, abstractMethod));
  }

  void checkEnumWithAbstractMethods(@NotNull PsiEnumConstant enumConstant) {
    PsiEnumConstantInitializer initializingClass = enumConstant.getInitializingClass();
    PsiClass enumClass = enumConstant.getContainingClass();
    PsiClass aClass = requireNonNullElse(initializingClass, enumClass);
    PsiMethod abstractMethod = ClassUtil.getAnyAbstractMethod(aClass);
    if (abstractMethod == null) return;

    PsiClass containingClass = abstractMethod.getContainingClass();
    if (containingClass == null || containingClass == initializingClass || 
        containingClass != enumClass && initializingClass == null && !hasEnumConstantsWithInitializer(enumClass)) {
      return;
    }

    myVisitor.report(JavaErrorKinds.CLASS_NO_ABSTRACT_METHOD.create(enumConstant, abstractMethod));
  }

  void checkExtendsDuplicate(@NotNull PsiJavaCodeReferenceElement element, PsiElement resolved) {
    if (!(element.getParent() instanceof PsiReferenceList list)) return;
    if (!(list.getParent() instanceof PsiClass)) return;
    if (!(resolved instanceof PsiClass aClass)) return;
    PsiManager manager = myVisitor.file().getManager();
    PsiJavaCodeReferenceElement sibling = PsiTreeUtil.getPrevSiblingOfType(element, PsiJavaCodeReferenceElement.class);
    while (true) {
      if (sibling == null) return;
      PsiElement target = sibling.resolve();
      if (manager.areElementsEquivalent(target, aClass)) break;
      sibling = PsiTreeUtil.getPrevSiblingOfType(sibling, PsiJavaCodeReferenceElement.class);
    }
    myVisitor.report(JavaErrorKinds.CLASS_REFERENCE_LIST_DUPLICATE.create(element, aClass));
  }

  void checkClassExtendsForeignInnerClass(@NotNull PsiJavaCodeReferenceElement extendRef, @Nullable PsiElement resolved) {
    PsiElement parent = extendRef.getParent();
    if (!(parent instanceof PsiReferenceList)) return;
    PsiElement grand = parent.getParent();
    if (!(grand instanceof PsiClass aClass)) return;
    PsiClass containerClass;
    if (aClass instanceof PsiTypeParameter typeParameter) {
      if (!(typeParameter.getOwner() instanceof PsiClass cls)) return;
      containerClass = cls;
    }
    else {
      containerClass = aClass;
    }
    if (aClass.getExtendsList() != parent && aClass.getImplementsList() != parent) return;
    if (resolved != null && !(resolved instanceof PsiClass)) {
      myVisitor.report(JavaErrorKinds.CLASS_REFERENCE_LIST_NAME_EXPECTED.create(extendRef));
      return;
    }
    extendRef.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiClass base) {
          PsiClass baseClass = base.getContainingClass();
          if (baseClass != null &&
              base.hasModifierProperty(PsiModifier.PRIVATE) &&
              baseClass == containerClass &&
              baseClass.getContainingClass() == null) {
            myVisitor.report(JavaErrorKinds.CLASS_REFERENCE_LIST_INNER_PRIVATE.create(reference, base));
            stopWalking();
            return;
          }

          // must be inner class
          if (!PsiUtil.isInnerClass(base)) return;

          if (resolve == resolved &&
              baseClass != null &&
              (!PsiTreeUtil.isAncestor(baseClass, extendRef, true) || aClass.hasModifierProperty(PsiModifier.STATIC)) &&
              !InheritanceUtil.hasEnclosingInstanceInScope(baseClass, extendRef, psiClass -> psiClass != aClass, true) &&
              !qualifiedNewCalledInConstructors(aClass)) {
            myVisitor.report(JavaErrorKinds.CLASS_REFERENCE_LIST_NO_ENCLOSING_INSTANCE.create(extendRef, baseClass));
            stopWalking();
          }
        }
      }
    });
  }

  void checkClassMustBeAbstract(@NotNull PsiClass aClass) {
    boolean mustCheck = aClass.isEnum() ? !hasEnumConstantsWithInitializer(aClass) :
                        !aClass.hasModifierProperty(PsiModifier.ABSTRACT) && aClass.getRBrace() != null;
    if (mustCheck) {
      checkClassWithAbstractMethods(aClass);
    }
  }

  void checkDuplicateNestedClass(PsiClass aClass) {
    String name = aClass.getName();
    if (name == null) return;
    PsiElement parent = aClass.getParent();
    boolean checkSiblings;
    if (parent instanceof PsiClass psiClass && !PsiUtil.isLocalOrAnonymousClass(psiClass) && !PsiUtil.isLocalOrAnonymousClass(aClass)) {
      // optimization: instead of iterating PsiClass children manually, we can get them all from caches
      PsiClass innerClass = psiClass.findInnerClassByName(name, false);
      if (innerClass != null && innerClass != aClass) {
        if (innerClass.getTextOffset() > aClass.getTextOffset()) {
          // report duplicate lower in text
          PsiClass c = innerClass;
          innerClass = aClass;
          aClass = c;
        }
        myVisitor.report(JavaErrorKinds.CLASS_DUPLICATE.create(aClass, innerClass));
        return;
      }
      checkSiblings = false; // there still might be duplicates in parents
    }
    else {
      checkSiblings = true;
    }
    if (!(parent instanceof PsiDeclarationStatement)) {
      parent = aClass;
    }
    while (parent != null) {
      if (parent instanceof PsiFile) break;
      PsiElement element = checkSiblings ? parent.getPrevSibling() : null;
      if (element == null) {
        element = parent.getParent();
        // JLS 14.3:
        // The name of a local class C may not be redeclared
        // as a local class of the directly enclosing method, constructor, or initializer block within the scope of C, or a compile-time
        // error occurs. However, a local class declaration may be shadowed (6.3.1)
        // anywhere inside a class declaration nested within the local class declaration's scope.
        if (element instanceof PsiMethod ||
            element instanceof PsiClass ||
            element instanceof PsiCodeBlock && element.getParent() instanceof PsiClassInitializer) {
          checkSiblings = false;
        }
      }
      parent = element;

      if (element instanceof PsiDeclarationStatement) element = PsiTreeUtil.getChildOfType(element, PsiClass.class);
      if (element instanceof PsiClass psiClass && name.equals(psiClass.getName())) {
        myVisitor.report(JavaErrorKinds.CLASS_DUPLICATE.create(aClass, psiClass));
      }
    }
  }

  void checkCyclicInheritance(@NotNull PsiClass aClass) {
    PsiClass circularClass = InheritanceUtil.getCircularClass(aClass);
    if (circularClass != null) {
      myVisitor.report(JavaErrorKinds.CLASS_CYCLIC_INHERITANCE.create(aClass, circularClass));
    }
  }
  
  void checkIllegalInstantiation(@NotNull PsiClass aClass, @NotNull PsiExpression highlightElement) {
    if (highlightElement instanceof PsiNewExpression newExpression && newExpression.isArrayCreation()) return;
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      myVisitor.report(JavaErrorKinds.INSTANTIATION_ABSTRACT.create(highlightElement, aClass));
    }
    if (aClass.isEnum()) {
      myVisitor.report(JavaErrorKinds.INSTANTIATION_ENUM.create(highlightElement));
    }
  }

  void checkDuplicateTopLevelClass(@NotNull PsiClass aClass) {
    if (aClass instanceof PsiImplicitClass) return;
    if (!(aClass.getParent() instanceof PsiFile)) return;
    String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) return;
    int numOfClassesToFind = 2;
    if (qualifiedName.contains("$")) {
      qualifiedName = qualifiedName.replace('$', '.');
      numOfClassesToFind = 1;
    }

    Module module = ModuleUtilCore.findModuleForPsiElement(aClass);
    if (module == null) return;

    GlobalSearchScope scope = GlobalSearchScope.moduleScope(module).intersectWith(aClass.getResolveScope());
    PsiClass[] classes = JavaPsiFacade.getInstance(myVisitor.project()).findClasses(qualifiedName, scope);
    if (aClass.getContainingFile() instanceof PsiJavaFile javaFile && javaFile.getPackageStatement() == null) {
      Collection<? extends PsiClass> implicitClasses =
        ImplicitClassSearch.search(qualifiedName, myVisitor.project(), scope).findAll();
      if (!implicitClasses.isEmpty()) {
        ArrayList<PsiClass> newClasses = new ArrayList<>();
        ContainerUtil.addAll(newClasses, classes);
        ContainerUtil.addAll(newClasses, implicitClasses);
        classes = newClasses.toArray(PsiClass.EMPTY_ARRAY);
      }
    }
    if (classes.length < numOfClassesToFind) return;
    checkDuplicateClasses(aClass, classes);
  }

  void checkDuplicateClassesWithImplicit(@NotNull PsiJavaFile file) {
    if (!myVisitor.isApplicable(JavaFeature.IMPLICIT_CLASSES)) return;
    PsiImplicitClass implicitClass = JavaImplicitClassUtil.getImplicitClassFor(file);
    if (implicitClass == null) return;

    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) return;

    GlobalSearchScope scope = GlobalSearchScope.moduleScope(module).intersectWith(implicitClass.getResolveScope());
    String qualifiedName = implicitClass.getQualifiedName();
    if (qualifiedName == null) return;
    PsiClass[] classes = JavaPsiFacade.getInstance(myVisitor.project()).findClasses(qualifiedName, scope);
    checkDuplicateClasses(implicitClass, classes);
  }

  private void checkDuplicateClasses(@NotNull PsiClass aClass, @NotNull PsiClass @NotNull [] classes) {
    PsiManager manager = aClass.getManager();
    Module module = ModuleUtilCore.findModuleForPsiElement(aClass);
    if (module == null) return;
    ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(aClass);
    if (virtualFile == null) return;
    boolean isTestSourceRoot = fileIndex.isInTestSourceContent(virtualFile);
    for (PsiClass dupClassCandidate : classes) {
      // do not use equals
      if (dupClassCandidate != aClass) {
        VirtualFile file = dupClassCandidate.getContainingFile().getVirtualFile();
        if (file != null && manager.isInProject(dupClassCandidate) && fileIndex.isInTestSourceContent(file) == isTestSourceRoot) {
          myVisitor.report(JavaErrorKinds.CLASS_DUPLICATE_IN_OTHER_FILE.create(aClass, dupClassCandidate));
          return;
        }
      }
    }
  }

  void checkMustNotBeLocal(@NotNull PsiClass aClass) {
    IElementType token;
    JavaFeature feature;
    if (aClass.isEnum()) {
      token = JavaTokenType.ENUM_KEYWORD;
      feature = JavaFeature.LOCAL_ENUMS;
    }
    else if (aClass.isInterface()) {
      token = JavaTokenType.INTERFACE_KEYWORD;
      feature = aClass.isAnnotationType() ? null : JavaFeature.LOCAL_INTERFACES;
    }
    else {
      return;
    }
    if (!PsiUtil.isLocalClass(aClass)) return;
    PsiElement anchor = Stream.iterate(aClass.getFirstChild(), Objects::nonNull, PsiElement::getNextSibling)
      .filter(e -> e instanceof PsiKeyword keyword && keyword.getTokenType().equals(token))
      .findFirst()
      .orElseThrow();
    if (feature == null) {
      myVisitor.report(JavaErrorKinds.ANNOTATION_LOCAL.create(anchor));
    } else {
      myVisitor.checkFeature(anchor, feature);
    }
  }

  void checkClassAndPackageConflict(@NotNull PsiClass aClass) {
    String name = aClass.getQualifiedName();
    if (name == null) return;
    if (CommonClassNames.DEFAULT_PACKAGE.equals(name)) {
      myVisitor.report(JavaErrorKinds.CLASS_CLASHES_WITH_PACKAGE.create(aClass));
    } else {
      PsiElement file = aClass.getParent();
      if (file instanceof PsiJavaFile javaFile && !javaFile.getPackageName().isEmpty()) {
        PsiDirectory directory = javaFile.getParent();
        if (directory != null) {
          String simpleName = aClass.getName();
          PsiDirectory subDirectory = simpleName == null ? null : directory.findSubdirectory(simpleName);
          if (subDirectory != null &&
              simpleName.equals(subDirectory.getName()) &&
              PsiTreeUtil.findChildOfType(subDirectory, PsiJavaFile.class) != null) {
            myVisitor.report(JavaErrorKinds.CLASS_CLASHES_WITH_PACKAGE.create(aClass));
          }
        }
      }
    }
  }

  void checkPublicClassInRightFile(@NotNull PsiClass aClass) {
    PsiFile containingFile = aClass.getContainingFile();
    if (aClass.getParent() != containingFile || !aClass.hasModifierProperty(PsiModifier.PUBLIC) || !(containingFile instanceof PsiJavaFile file))
      return;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null || virtualFile.getNameWithoutExtension().equals(aClass.getName())) {
      return;
    }
    if (JavaPsiSingleFileSourceUtil.isJavaHashBangScript(file)) return;
    myVisitor.report(JavaErrorKinds.CLASS_WRONG_FILE_NAME.create(aClass));
  }

  void checkSealedClassInheritors(@NotNull PsiClass psiClass) {
    if (psiClass.hasModifierProperty(PsiModifier.SEALED)) {
      PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
      if (nameIdentifier == null) return;
      if (psiClass.isEnum()) return;

      Collection<PsiClass> inheritors = DirectClassInheritorsSearch.searchAllSealedInheritors(
        psiClass, GlobalSearchScope.allScope(myVisitor.project()).union(GlobalSearchScope.fileScope(myVisitor.file()))).findAll();
      if (inheritors.isEmpty()) {
        myVisitor.report(JavaErrorKinds.CLASS_SEALED_NO_INHERITORS.create(psiClass));
        return;
      }
      PsiFile parentFile = psiClass.getContainingFile();
      PsiManager manager = parentFile.getManager();
      boolean hasOutsideClasses = ContainerUtil.exists(inheritors, inheritor -> !manager.areElementsEquivalent(
        inheritor.getNavigationElement().getContainingFile(), parentFile));
      if (hasOutsideClasses) {
        Map<PsiJavaCodeReferenceElement, PsiClass> permittedClassesRefs = getPermittedClassesRefs(psiClass);
        Collection<PsiClass> permittedClasses = permittedClassesRefs.values();
        boolean hasMissingInheritors = ContainerUtil.exists(inheritors, inheritor -> !permittedClasses.contains(inheritor));
        if (hasMissingInheritors) {
          myVisitor.report(JavaErrorKinds.CLASS_SEALED_INCOMPLETE_PERMITS.create(psiClass));
        }
      }
    }
  }

  void checkSealedSuper(@NotNull PsiClass aClass) {
    PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
    if (nameIdentifier != null &&
        !(aClass instanceof PsiTypeParameter) &&
        !aClass.hasModifierProperty(PsiModifier.SEALED) &&
        !aClass.hasModifierProperty(PsiModifier.NON_SEALED) &&
        !aClass.hasModifierProperty(PsiModifier.FINAL) &&
        Arrays.stream(aClass.getSuperTypes())
          .map(type -> type.resolve())
          .anyMatch(superClass -> superClass != null && superClass.hasModifierProperty(PsiModifier.SEALED))) {
      boolean canBeFinal = !aClass.isInterface() && DirectClassInheritorsSearch.search(aClass).findFirst() == null;
      JavaErrorKind.Simple<PsiClass> errorKind = canBeFinal
                                              ? JavaErrorKinds.CLASS_SEALED_INHERITOR_EXPECTED_MODIFIERS_CAN_BE_FINAL
                                              : JavaErrorKinds.CLASS_SEALED_INHERITOR_EXPECTED_MODIFIERS;
      myVisitor.report(errorKind.create(aClass));
    }
  }

  void checkImplicitClassWellFormed(@NotNull PsiJavaFile file) {
    if (!myVisitor.isApplicable(JavaFeature.IMPLICIT_CLASSES)) return;
    PsiImplicitClass implicitClass = JavaImplicitClassUtil.getImplicitClassFor(file);
    if (implicitClass == null) return;
    String name = implicitClass.getQualifiedName();
    if (!PsiNameHelper.getInstance(myVisitor.project()).isIdentifier(name)) {
      myVisitor.report(JavaErrorKinds.CLASS_IMPLICIT_INVALID_FILE_NAME.create(file, implicitClass));
      return;
    }
    PsiMethod[] methods = implicitClass.getMethods();
    boolean hasMainMethod = ContainerUtil.exists(methods, method -> "main".equals(method.getName()) && PsiMethodUtil.isMainMethod(method));
    if (!hasMainMethod) {
      myVisitor.report(JavaErrorKinds.CLASS_IMPLICIT_NO_MAIN_METHOD.create(file, implicitClass));
    }
  }

  void checkImplicitClassMember(@NotNull PsiMember member) {
    if (!(member.getContainingClass() instanceof PsiImplicitClass)) return;

    PsiElement anchor = member;
    if (member instanceof PsiNameIdentifierOwner owner) {
      PsiElement nameIdentifier = owner.getNameIdentifier();
      if (nameIdentifier != null) {
        anchor = nameIdentifier;
      }
    }
    myVisitor.checkFeature(anchor, JavaFeature.IMPLICIT_CLASSES);
  }

  void checkIllegalInstanceMemberInRecord(@NotNull PsiMember member) {
    if (!member.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass aClass = member.getContainingClass();
      if (aClass != null && aClass.isRecord()) {
        var error = member instanceof PsiClassInitializer initializer ?
                    JavaErrorKinds.RECORD_INSTANCE_INITIALIZER.create(initializer) :
                    JavaErrorKinds.RECORD_INSTANCE_FIELD.create((PsiField)member);
        myVisitor.report(error);
      }
    }
  }

  void checkThingNotAllowedInInterface(@NotNull PsiMember member) {
    PsiClass aClass = member.getContainingClass();
    if (aClass == null || !aClass.isInterface()) return;
    if (member instanceof PsiMethod method && method.isConstructor()) {
      myVisitor.report(JavaErrorKinds.INTERFACE_CONSTRUCTOR.create(method));
    } else if (member instanceof PsiClassInitializer initializer) {
      myVisitor.report(JavaErrorKinds.INTERFACE_CLASS_INITIALIZER.create(initializer));
    }
  }

  void checkInitializersInImplicitClass(@NotNull PsiClassInitializer initializer) {
    if (initializer.getContainingClass() instanceof PsiImplicitClass && myVisitor.isApplicable(JavaFeature.IMPLICIT_CLASSES)) {
      myVisitor.report(JavaErrorKinds.CLASS_IMPLICIT_INITIALIZER.create(initializer));
    }
  }

  void checkPackageNotAllowedInImplicitClass(@NotNull PsiPackageStatement statement) {
    if (myVisitor.isApplicable(JavaFeature.IMPLICIT_CLASSES) && JavaImplicitClassUtil.isFileWithImplicitClass(myVisitor.file())) {
      myVisitor.report(JavaErrorKinds.CLASS_IMPLICIT_PACKAGE.create(statement));
    }
  }

  void checkClassRestrictedKeyword(@NotNull PsiIdentifier identifier) {
    String className = identifier.getText();
    if (PsiTypesUtil.isRestrictedIdentifier(className, myVisitor.languageLevel())) {
      myVisitor.report(JavaErrorKinds.IDENTIFIER_RESTRICTED.create(identifier));
    }
  }

  void checkStaticDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
    if (!myVisitor.hasErrorResults()) checkStaticClassDeclarationInInnerClass(keyword);
    if (!myVisitor.hasErrorResults()) checkStaticMemberInInnerClass(keyword);
  }

  private void checkStaticClassDeclarationInInnerClass(@NotNull PsiKeyword keyword) {
    // keyword points to 'class' or 'interface' or 'enum'; interface and enum are implicitly static
    if (!(keyword.getParent() instanceof PsiClass curClass)) return;
    if (!curClass.hasModifierProperty(PsiModifier.STATIC) || PsiUtilCore.hasErrorElementChild(curClass)) return;
    if (!(curClass.getParent() instanceof PsiClass parentClass)) return;
    if (parentClass.hasModifierProperty(PsiModifier.STATIC)) return;
    PsiElement parent = parentClass.getParent();
    if (!(parent instanceof PsiClass) && !(parent instanceof PsiDeclarationStatement) &&
        !(parent instanceof PsiNewExpression) && !(parent instanceof PsiEnumConstant)) {
      return;
    }
    // highlight 'static' keyword if any, or class or interface if not
    PsiElement context = keyword;
    PsiModifierList modifierList = curClass.getModifierList();
    if (modifierList != null) {
      for (PsiElement element = modifierList.getFirstChild(); element != null; element = element.getNextSibling()) {
        if (Objects.equals(element.getText(), PsiModifier.STATIC)) {
          context = element;
          break;
        }
      }
    }
    myVisitor.checkFeature(context, JavaFeature.INNER_STATICS);
  }

  private void checkStaticMemberInInnerClass(@NotNull PsiKeyword keyword) {
    if (!keyword.getTokenType().equals(JavaTokenType.STATIC_KEYWORD)) return;
    if (!(keyword.getParent() instanceof PsiModifierList modifierList)) return;
    if (!(modifierList.getParent() instanceof PsiMember member)) return;
    if (member instanceof PsiClass) return; // checked separately
    if (PsiUtilCore.hasErrorElementChild(member)) return;
    if (member instanceof PsiField field && PsiUtil.isCompileTimeConstant(field)) return;
    if (!(member.getParent() instanceof PsiClass psiClass)) return;
    if (psiClass.hasModifierProperty(PsiModifier.STATIC)) return;
    PsiElement classParent = psiClass.getParent();
    if (!(classParent instanceof PsiClass) && !(classParent instanceof PsiDeclarationStatement) &&
        !(classParent instanceof PsiNewExpression) && !(classParent instanceof PsiEnumConstant)) {
      return;
    }
    myVisitor.checkFeature(keyword, JavaFeature.INNER_STATICS);
  }

  void checkExtendsAllowed(@NotNull PsiReferenceList list) {
    if (list.getParent() instanceof PsiClass aClass && list.equals(aClass.getExtendsList())) {
      if (aClass.isRecord()) {
        myVisitor.report(JavaErrorKinds.RECORD_EXTENDS.create(list));
      }
      else if (aClass.isEnum()) {
        myVisitor.report(JavaErrorKinds.ENUM_EXTENDS.create(list));
      }
    }
  }

  void checkImplementsAllowed(@NotNull PsiReferenceList list) {
    if (list.getParent() instanceof PsiClass aClass && aClass.isInterface()) {
      boolean isImplements = list.equals(aClass.getImplementsList());
      if (isImplements) {
        myVisitor.report(JavaErrorKinds.INTERFACE_IMPLEMENTS.create(list));
      }
    }
  }

  void checkClassExtendsOnlyOneClass(@NotNull PsiReferenceList list) {
    if (!(list.getParent() instanceof PsiClass aClass)) return;
    PsiClassType[] referencedTypes = list.getReferencedTypes();
    if (!aClass.isInterface() && referencedTypes.length > 1 && aClass.getExtendsList() == list) {
      myVisitor.report(JavaErrorKinds.CLASS_CANNOT_EXTEND_MULTIPLE_CLASSES.create(list));
    }
  }

  void checkPermitsList(@NotNull PsiReferenceList list) {
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiClass aClass) || !list.equals(aClass.getPermitsList())) return;
    myVisitor.checkFeature(list, JavaFeature.SEALED_CLASSES);
    if (myVisitor.hasErrorResults()) return;
    PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
    if (nameIdentifier == null) return;
    if (aClass.isEnum() || aClass.isRecord() || aClass.isAnnotationType()) {
      JavaErrorKind.Simple<PsiReferenceList> description;
      if (aClass.isEnum()) description = JavaErrorKinds.ENUM_PERMITS;
      else if (aClass.isRecord()) description = JavaErrorKinds.RECORD_PERMITS;
      else description = JavaErrorKinds.ANNOTATION_PERMITS;
      myVisitor.report(description.create(list));
      return;
    }
    if (!aClass.hasModifierProperty(PsiModifier.SEALED)) {
      myVisitor.report(JavaErrorKinds.CLASS_SEALED_PERMITS_ON_NON_SEALED.create(aClass));
      return;
    }
    PsiJavaModule currentModule = myVisitor.javaModule();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myVisitor.project());
    for (PsiJavaCodeReferenceElement permitted : list.getReferenceElements()) {
      for (PsiAnnotation annotation : PsiTreeUtil.findChildrenOfType(permitted, PsiAnnotation.class)) {
        myVisitor.report(JavaErrorKinds.ANNOTATION_NOT_ALLOWED_IN_PERMIT_LIST.create(annotation));
      }

      PsiReferenceParameterList parameterList = permitted.getParameterList();
      if (parameterList != null && parameterList.getTypeParameterElements().length > 0) {
        myVisitor.report(JavaErrorKinds.TYPE_ARGUMENT_IN_PERMITS_LIST.create(parameterList));
        continue;
      }
      @Nullable PsiElement resolve = permitted.resolve();
      if (resolve instanceof PsiClass inheritorClass) {
        PsiManager manager = inheritorClass.getManager();
        if (!ContainerUtil.exists(inheritorClass.getSuperTypes(), type -> manager.areElementsEquivalent(aClass, type.resolve()))) {
          myVisitor.report(JavaErrorKinds.CLASS_PERMITTED_NOT_DIRECT_SUBCLASS.create(
            permitted, new JavaErrorKinds.SuperclassSubclassContext(aClass, inheritorClass)));
        }
        else if (currentModule == null && !psiFacade.arePackagesTheSame(aClass, inheritorClass)) {
          myVisitor.report(JavaErrorKinds.CLASS_EXTENDS_SEALED_ANOTHER_PACKAGE.create(
            permitted, new JavaErrorKinds.SuperclassSubclassContext(aClass, inheritorClass)));
        }
        else if (currentModule != null && !areModulesTheSame(currentModule, JavaPsiModuleUtil.findDescriptorByElement(inheritorClass))) {
          myVisitor.report(JavaErrorKinds.CLASS_EXTENDS_SEALED_ANOTHER_MODULE.create(
            permitted, new JavaErrorKinds.SuperclassSubclassContext(aClass, inheritorClass)));
        }
        else if (!(inheritorClass instanceof PsiCompiledElement) && !hasPermittedSubclassModifier(inheritorClass)) {
          myVisitor.report(JavaErrorKinds.CLASS_PERMITTED_MUST_HAVE_MODIFIER.create(permitted, inheritorClass));
        }
      }
    }
  }

  private static boolean areModulesTheSame(@NotNull PsiJavaModule module, PsiJavaModule module1) {
    return module1 != null && module.getOriginalElement() == module1.getOriginalElement();
  }

  private static boolean hasPermittedSubclassModifier(@NotNull PsiClass psiClass) {
    PsiModifierList modifiers = psiClass.getModifierList();
    if (modifiers == null) return false;
    return modifiers.hasModifierProperty(PsiModifier.SEALED) ||
           modifiers.hasModifierProperty(PsiModifier.NON_SEALED) ||
           modifiers.hasModifierProperty(PsiModifier.FINAL);
  }

  void checkExtendsClassAndImplementsInterface(@NotNull PsiReferenceList referenceList,
                                               @NotNull PsiClass extendFrom,
                                               @NotNull PsiJavaCodeReferenceElement ref) {
    PsiClass aClass = (PsiClass)referenceList.getParent();
    boolean isImplements = referenceList.equals(aClass.getImplementsList());
    boolean isInterface = aClass.isInterface();
    if (isInterface && isImplements) return;
    boolean mustBeInterface = isImplements || isInterface;
    if (extendFrom.isInterface() == mustBeInterface) return;

    JavaErrorKind.Parameterized<PsiJavaCodeReferenceElement, PsiClass> error;
    if (isInterface) {
      error = JavaErrorKinds.INTERFACE_EXTENDS_CLASS;
    } else if (isImplements) {
      error = JavaErrorKinds.CLASS_IMPLEMENTS_CLASS;
    } else {
      error = JavaErrorKinds.CLASS_EXTENDS_INTERFACE;
    }
    myVisitor.report(error.create(ref, aClass));
  }

  void checkValueClassExtends(@NotNull PsiClass superClass,
                              @NotNull PsiClass psiClass,
                              @NotNull PsiJavaCodeReferenceElement ref) {
    if (!(!psiClass.isValueClass() ||
          superClass.isValueClass() ||
          CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName()))) {
      myVisitor.report(JavaErrorKinds.VALUE_CLASS_EXTENDS_NON_ABSTRACT.create(ref));
    }
  }

  void checkCannotInheritFromFinal(@NotNull PsiClass superClass, @NotNull PsiJavaCodeReferenceElement elementToHighlight) {
    if (superClass.hasModifierProperty(PsiModifier.FINAL) || superClass.isEnum()) {
      myVisitor.report(JavaErrorKinds.CLASS_EXTENDS_FINAL.create(elementToHighlight, superClass));
    }
  }

  void checkExtendsProhibitedClass(@NotNull PsiClass superClass,
                                   @NotNull PsiClass psiClass,
                                   @NotNull PsiJavaCodeReferenceElement elementToHighlight) {
    String qualifiedName = superClass.getQualifiedName();
    if (CommonClassNames.JAVA_LANG_ENUM.equals(qualifiedName) && !psiClass.isEnum() ||
        CommonClassNames.JAVA_LANG_RECORD.equals(qualifiedName) && !psiClass.isRecord()) {
      myVisitor.report(JavaErrorKinds.CLASS_EXTENDS_PROHIBITED_CLASS.create(elementToHighlight, superClass));
    }
  }

  void checkAnonymousInheritFinal(@NotNull PsiNewExpression expression) {
    PsiAnonymousClass aClass = PsiTreeUtil.getChildOfType(expression, PsiAnonymousClass.class);
    if (aClass == null) return;
    PsiClassType baseClassReference = aClass.getBaseClassType();
    PsiClass baseClass = baseClassReference.resolve();
    if (baseClass == null) return;
    checkCannotInheritFromFinal(baseClass, aClass.getBaseClassReference());
  }

  void checkAnonymousInheritProhibited(@NotNull PsiNewExpression expression) {
    PsiAnonymousClass aClass = expression.getAnonymousClass();
    if (aClass != null) {
      PsiClass superClass = aClass.getSuperClass();
      PsiJavaCodeReferenceElement reference = expression.getClassOrAnonymousClassReference();
      if (superClass != null && reference != null) {
        checkExtendsProhibitedClass(superClass, aClass, reference);
      }
    }
  }

  void checkAnonymousSealedProhibited(@NotNull PsiNewExpression newExpression) {
    PsiAnonymousClass aClass = newExpression.getAnonymousClass();
    if (aClass != null) {
      PsiClass superClass = aClass.getBaseClassType().resolve();
      if (superClass != null && superClass.hasModifierProperty(PsiModifier.SEALED)) {
        myVisitor.report(JavaErrorKinds.CLASS_ANONYMOUS_EXTENDS_SEALED.create(aClass));
      }
    }
  }

  void checkClassAlreadyImported(@NotNull PsiClass aClass) {
    PsiFile file = aClass.getContainingFile();
    if (!(file instanceof PsiJavaFile javaFile)) return;
    // check only top-level classes conflicts
    if (aClass.getParent() != javaFile) return;
    PsiImportList importList = javaFile.getImportList();
    if (importList == null) return;
    PsiImportStatementBase[] importStatements = importList.getAllImportStatements();
    for (PsiImportStatementBase importStatement : importStatements) {
      if (importStatement.isOnDemand()) continue;
      PsiElement resolved = importStatement.resolve();
      if (resolved instanceof PsiClass psiClass && !resolved.equals(aClass) && Comparing.equal(aClass.getName(), psiClass.getName(), true)) {
        myVisitor.report(JavaErrorKinds.CLASS_ALREADY_IMPORTED.create(aClass));
      }
    }
  }

  void checkClassDoesNotCallSuperConstructorOrHandleExceptions(PsiClass aClass) {
    if (aClass.isEnum()) return;
    // Check only no-ctr classes. Problem with a specific constructor will be highlighted inside it
    if (aClass.getConstructors().length != 0) return;
    // find no-args base class ctr
    checkBaseClassDefaultConstructorProblem(aClass, aClass, PsiClassType.EMPTY_ARRAY);
  }

  void checkBaseClassDefaultConstructorProblem(@NotNull PsiClass aClass,
                                               @NotNull PsiMember anchor,
                                               PsiClassType @NotNull [] handledExceptions) {
    if (aClass instanceof PsiAnonymousClass) return;
    PsiClass baseClass = aClass.getSuperClass();
    if (baseClass == null) return;
    PsiMethod[] constructors = baseClass.getConstructors();
    if (constructors.length == 0) return;

    PsiElement resolved = JavaResolveUtil.resolveImaginarySuperCallInThisPlace(aClass, myVisitor.project(), baseClass);
    List<PsiMethod> constructorCandidates = (resolved != null ? Collections.singletonList((PsiMethod)resolved)
                                                              : Arrays.asList(constructors))
      .stream()
      .filter(constructor -> {
        PsiParameter[] parameters = constructor.getParameterList().getParameters();
        return (parameters.length == 0 || parameters.length == 1 && parameters[0].isVarArgs()) &&
               PsiResolveHelper.getInstance(myVisitor.project()).isAccessible(constructor, aClass, null);
      })
      .limit(2).toList();

    if (constructorCandidates.size() >= 2) {// two ambiguous var-args-only constructors
      var context =
        new JavaErrorKinds.AmbiguousImplicitConstructorCallContext(aClass, constructorCandidates.get(0), constructorCandidates.get(1));
      myVisitor.report(JavaErrorKinds.CONSTRUCTOR_AMBIGUOUS_IMPLICIT_CALL.create(anchor, context));
    } else if (!constructorCandidates.isEmpty()) {
      checkDefaultConstructorThrowsException(constructorCandidates.get(0), anchor, handledExceptions);
    } else {
      // no need to distract with missing constructor error when there is already a "Cannot inherit from final class" error message
      if (baseClass.hasModifierProperty(PsiModifier.FINAL)) return;
      myVisitor.report(JavaErrorKinds.CONSTRUCTOR_NO_DEFAULT.create(anchor, baseClass));
    }
  }

  private void checkDefaultConstructorThrowsException(@NotNull PsiMethod constructor, @NotNull PsiMember anchor, PsiClassType[] handledExceptions) {
    PsiClassType[] referencedTypes = constructor.getThrowsList().getReferencedTypes();
    List<PsiClassType> exceptions = new ArrayList<>();
    for (PsiClassType referencedType : referencedTypes) {
      if (!ExceptionUtil.isUncheckedException(referencedType) && !ExceptionUtil.isHandledBy(referencedType, handledExceptions)) {
        exceptions.add(referencedType);
      }
    }
    if (!exceptions.isEmpty()) {
      myVisitor.report(JavaErrorKinds.EXCEPTION_UNHANDLED.create(anchor, exceptions));
    }
  }

  void checkConstructorCallsBaseClassConstructor(@NotNull PsiMethod constructor) {
    if (!constructor.isConstructor()) return;
    PsiClass aClass = constructor.getContainingClass();
    if (aClass == null) return;
    if (aClass.isEnum()) return;
    PsiCodeBlock body = constructor.getBody();
    if (body == null) return;

    if (JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor) != null) return;
    PsiClassType[] handledExceptions = constructor.getThrowsList().getReferencedTypes();
    checkBaseClassDefaultConstructorProblem(aClass, constructor, handledExceptions);
  }

  void checkEnumSuperConstructorCall(@NotNull PsiMethodCallExpression expr) {
    PsiReferenceExpression methodExpression = expr.getMethodExpression();
    PsiElement refNameElement = methodExpression.getReferenceNameElement();
    if (refNameElement != null && JavaKeywords.SUPER.equals(refNameElement.getText())) {
      PsiMember constructor = PsiUtil.findEnclosingConstructorOrInitializer(expr);
      if (constructor instanceof PsiMethod) {
        PsiClass aClass = constructor.getContainingClass();
        if (aClass != null && aClass.isEnum()) {
          myVisitor.report(JavaErrorKinds.CALL_SUPER_ENUM_CONSTRUCTOR.create(expr));
        }
      }
    }
  }

  void checkSuperQualifierType(@NotNull PsiMethodCallExpression superCall) {
    if (!JavaPsiConstructorUtil.isSuperConstructorCall(superCall)) return;
    PsiMethod ctr = PsiTreeUtil.getParentOfType(superCall, PsiMethod.class, true, PsiMember.class);
    if (ctr == null) return;
    PsiClass aClass = ctr.getContainingClass();
    if (aClass == null) return;
    PsiClass targetClass = aClass.getSuperClass();
    if (targetClass == null) return;
    PsiExpression qualifier = superCall.getMethodExpression().getQualifierExpression();
    if (qualifier != null) {
      if (isRealInnerClass(targetClass)) {
        PsiClass outerClass = targetClass.getContainingClass();
        if (outerClass != null) {
          PsiClassType outerType = myVisitor.factory().createType(outerClass);
          myVisitor.myExpressionChecker.checkAssignability(outerType, null, qualifier, qualifier);
        }
      } else {
        myVisitor.report(JavaErrorKinds.CALL_SUPER_QUALIFIER_NOT_INNER_CLASS.create(qualifier, targetClass));
      }
    }
  }

  /** JLS 8.1.3. Inner Classes and Enclosing Instances */
  private static boolean isRealInnerClass(PsiClass aClass) {
    if (PsiUtil.isInnerClass(aClass)) return true;
    if (!PsiUtil.isLocalOrAnonymousClass(aClass)) return false;
    if (aClass.hasModifierProperty(PsiModifier.STATIC)) return false; // check for implicit staticness
    PsiMember member = PsiTreeUtil.getParentOfType(aClass, PsiMember.class, true);
    return member != null && !member.hasModifierProperty(PsiModifier.STATIC);
  }

  private static @Unmodifiable @NotNull Map<PsiJavaCodeReferenceElement, PsiClass> getPermittedClassesRefs(@NotNull PsiClass psiClass) {
    PsiReferenceList permitsList = psiClass.getPermitsList();
    if (permitsList == null) return Collections.emptyMap();
    PsiJavaCodeReferenceElement[] classRefs = permitsList.getReferenceElements();
    return ContainerUtil.map2Map(classRefs, r -> Pair.create(r, ObjectUtils.tryCast(r.resolve(), PsiClass.class)));
  }

  /**
   * 15.9 Class Instance Creation Expressions | 15.9.2 Determining Enclosing Instances
   */
  private static boolean qualifiedNewCalledInConstructors(@NotNull PsiClass aClass) {
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) return false;
    for (PsiMethod constructor : constructors) {
      PsiMethodCallExpression methodCallExpression = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
      if (methodCallExpression == null) return false;
      if (JavaPsiConstructorUtil.isChainedConstructorCall(methodCallExpression)) continue;
      PsiReferenceExpression referenceExpression = methodCallExpression.getMethodExpression();
      PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(referenceExpression.getQualifierExpression());
      //If the class instance creation expression is qualified, then the immediately
      //enclosing instance of i is the object that is the value of the Primary expression or the ExpressionName,
      //otherwise aClass needs to be a member of a class enclosing the class in which the class instance creation expression appears
      //already excluded by InheritanceUtil.hasEnclosingInstanceInScope
      if (qualifierExpression == null) return false;
    }
    return true;
  }

  private static boolean hasEnumConstantsWithInitializer(@NotNull PsiClass aClass) {
    return CachedValuesManager.getCachedValue(aClass, () -> {
      PsiField[] fields = aClass.getFields();
      for (PsiField field : fields) {
        if (field instanceof PsiEnumConstant constant && constant.getInitializingClass() != null) {
          return new CachedValueProvider.Result<>(true, PsiModificationTracker.MODIFICATION_COUNT);
        }
      }
      return new CachedValueProvider.Result<>(false, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  void checkImplicitThisReferenceBeforeSuper(@NotNull PsiClass aClass) {
    if (myVisitor.sdkVersion().isAtLeast(JavaSdkVersion.JDK_1_7)) return;
    if (aClass instanceof PsiAnonymousClass || aClass instanceof PsiTypeParameter) return;
    PsiClass superClass = aClass.getSuperClass();
    if (superClass == null || !PsiUtil.isInnerClass(superClass)) return;
    PsiClass outerClass = superClass.getContainingClass();
    if (!InheritanceUtil.isInheritorOrSelf(aClass, outerClass, true)) return;
    // 'this' can be used as an (implicit) super() qualifier
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      myVisitor.report(JavaErrorKinds.REFERENCE_MEMBER_BEFORE_CONSTRUCTOR.create(aClass, aClass.getName() + ".this"));
      return;
    }
    for (PsiMethod constructor : constructors) {
      PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
      if (!JavaPsiConstructorUtil.isSuperConstructorCall(call)) {
        myVisitor.report(JavaErrorKinds.REFERENCE_MEMBER_BEFORE_CONSTRUCTOR.create(constructor, aClass.getName() + ".this"));
        return;
      }
    }
  }

  void checkExtendsSealedClass(@NotNull PsiClass aClass,
                               @NotNull PsiClass superClass,
                               @NotNull PsiJavaCodeReferenceElement elementToHighlight) {
    if (superClass.hasModifierProperty(PsiModifier.SEALED)) {
      if (PsiUtil.isLocalClass(aClass)) {
        myVisitor.report(JavaErrorKinds.CLASS_EXTENDS_SEALED_LOCAL.create(elementToHighlight, aClass));
        return;
      }
      if (!JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, superClass) &&
          JavaPsiModuleUtil.findDescriptorByElement(aClass) == null) {
        myVisitor.report(JavaErrorKinds.CLASS_EXTENDS_SEALED_ANOTHER_PACKAGE.create(
          elementToHighlight, new JavaErrorKinds.SuperclassSubclassContext(superClass, aClass)));
      }

      PsiClassType[] permittedTypes = superClass.getPermitsListTypes();
      if (permittedTypes.length > 0) {
        PsiManager manager = superClass.getManager();
        if (ContainerUtil.exists(permittedTypes, permittedType -> manager.areElementsEquivalent(aClass, permittedType.resolve()))) {
          return;
        }
      }
      else if (aClass.getContainingFile() == superClass.getContainingFile()) {
        return;
      }
      PsiIdentifier identifier = aClass.getNameIdentifier();
      if (identifier == null) return;
      myVisitor.report(JavaErrorKinds.CLASS_EXTENDS_SEALED_NOT_PERMITTED.create(
        elementToHighlight, new JavaErrorKinds.SuperclassSubclassContext(superClass, aClass)));
    }
  }

}
