/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.javac.ast;

import com.intellij.util.containers.Stack;
import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacNameTable;
import org.jetbrains.jps.javac.ast.api.JavacRef;

import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

class JavacTreeRefScanner extends TreeScanner<Tree, JavacReferenceIndexListener.ReferenceCollector> {
  private static final Set<ElementKind> ALLOWED_ELEMENTS = EnumSet.of(ElementKind.ENUM,
                                                                      ElementKind.CLASS,
                                                                      ElementKind.ANNOTATION_TYPE,
                                                                      ElementKind.INTERFACE,
                                                                      ElementKind.ENUM_CONSTANT,
                                                                      ElementKind.FIELD,
                                                                      ElementKind.CONSTRUCTOR,
                                                                      ElementKind.METHOD);

  @Override
  public Tree visitCompilationUnit(CompilationUnitTree node, JavacReferenceIndexListener.ReferenceCollector refCollector) {
    scan(node.getPackageAnnotations(), refCollector);
    scan(node.getTypeDecls(), refCollector);
    return node;
  }

  @Override
  public Tree visitIdentifier(IdentifierTree node, JavacReferenceIndexListener.ReferenceCollector refCollector) {
    final Element element = refCollector.getReferencedElement(node);
    if (element == null) {
      return null;
    }
    if (ALLOWED_ELEMENTS.contains(element.getKind())) {
      refCollector.sinkReference(refCollector.asJavacRef(element));
    }
    return null;
  }

  @Override
  public Tree visitNewClass(NewClassTree node, JavacReferenceIndexListener.ReferenceCollector collector) {
    if (node.getClassBody() == null) {
      final Element element = collector.getReferencedElement(node);
      if (element != null) {
        collector.sinkReference(collector.asJavacRef(element));
      }
    }
    return super.visitNewClass(node, collector);
  }

  @Override
  public Tree visitVariable(VariableTree node, JavacReferenceIndexListener.ReferenceCollector refCollector) {
    final Element element = refCollector.getReferencedElement(node);
    if (element != null && element.getKind() == ElementKind.FIELD) {
      final JavacRef.JavacElementRefBase ref = refCollector.asJavacRef(element);
      if (ref != null) {
        processMemberDefinition(refCollector, ref, element, element.asType());
      }
    }
    return super.visitVariable(node, refCollector);
  }

  @Override
  public Tree visitMemberSelect(MemberSelectTree node, JavacReferenceIndexListener.ReferenceCollector refCollector) {
    final Element element = refCollector.getReferencedElement(node);
    if (element != null && element.getKind() != ElementKind.PACKAGE) {
      ExpressionTree qualifierExpression = node.getExpression();
      Element qualifierType = null;
      TypeMirror type = refCollector.getType(qualifierExpression);
      if (type instanceof DeclaredType) {
        qualifierType = ((DeclaredType)type).asElement();
      }
      refCollector.sinkReference(refCollector.asJavacRef(element, qualifierType));
    }
    return super.visitMemberSelect(node, refCollector);
  }

  @Override
  public Tree visitMethod(MethodTree node, JavacReferenceIndexListener.ReferenceCollector refCollector) {
    final Element element = refCollector.getReferencedElement(node);

    if (refCollector.getNameTable().isInit(node.getName()) &&
        myCurrentEnclosingElementOffset.peek() == refCollector.getStartOffset(node)) {
      return null;
    }

    if (element != null) {
      final JavacRef.JavacElementRefBase ref = refCollector.asJavacRef(element);
      if (ref != null) {
        processMemberDefinition(refCollector, ref, element, ((ExecutableElement)element).getReturnType());
      }
    }
    return super.visitMethod(node, refCollector);
  }

  private void processMemberDefinition(JavacReferenceIndexListener.ReferenceCollector refCollector,
                                       JavacRef.JavacElementRefBase ref,
                                       Element element,
                                       TypeMirror retType) {
    refCollector.sinkReference(ref);
    byte dimension = 0;
    if (retType.getKind() == TypeKind.ARRAY) {
      retType = ((ArrayType)retType).getComponentType();
      dimension = 1;
    }
    else if (retType.getKind() == TypeKind.DECLARED) {
      List<? extends TypeMirror> typeArguments = ((DeclaredType)retType).getTypeArguments();
      if (typeArguments.size() == 1 && isIterator((TypeElement)((DeclaredType)retType).asElement(), refCollector)) {
        dimension = -1;
        retType = typeArguments.get(0);
      }
    }
    final JavacRef.JavacElementRefBase returnType = refCollector.asJavacRef(retType);
    if (returnType != null) {
      refCollector.sinkDeclaration(new JavacDef.JavacMemberDef(ref, returnType, dimension, isStatic(element)));
    }
  }

  @Override
  public Tree visitMethodInvocation(MethodInvocationTree node, JavacReferenceIndexListener.ReferenceCollector collector) {
    if (node.getMethodSelect() instanceof IdentifierTree) {
      Element element = collector.getReferencedElement(node.getMethodSelect());
      if (element != null && element.getKind() != ElementKind.CONSTRUCTOR) {
        Set<Modifier> modifiers = element.getModifiers();
        if (!modifiers.contains(Modifier.STATIC) && !modifiers.contains(Modifier.PRIVATE)) {
          TypeElement currentClass = myCurrentEnclosingElement.peek();
          TypeElement actualQualifier = findQualifier(element, currentClass);
          //means java.lang.Object's method called from an interface
          if (actualQualifier == null) {
            actualQualifier = myCurrentEnclosingElement.peek();
          }
          collector.sinkReference(collector.asJavacRef(element, actualQualifier));
          scan(node.getTypeArguments(), collector);
          scan(node.getArguments(), collector);
          return null;
        }
      }
    }
    return super.visitMethodInvocation(node, collector);
  }

  private final Stack<TypeElement> myCurrentEnclosingElement = new Stack<TypeElement>(1);
  private final Stack<Long> myCurrentEnclosingElementOffset = new Stack<Long>(1);

  @Override
  public Tree visitClass(ClassTree node, JavacReferenceIndexListener.ReferenceCollector refCollector) {
    TypeElement element = (TypeElement)refCollector.getReferencedElement(node);
    if (element == null) return null;
    myCurrentEnclosingElement.add(element);
    ModifiersTree modifiers = node.getModifiers();
    long modifiersEndOffset = refCollector.getEndOffset(modifiers);
    long startOffset = modifiersEndOffset == -1 ? refCollector.getStartOffset(node) : (modifiersEndOffset + 1);
    myCurrentEnclosingElementOffset.add(startOffset);
    try {
      final TypeMirror superclass = element.getSuperclass();
      final List<? extends TypeMirror> interfaces = element.getInterfaces();
      final JavacRef[] supers;
      if (superclass != refCollector.getTypeUtility().getNoType(TypeKind.NONE)) {
        supers = new JavacRef[interfaces.size() + 1];
        final JavacRef.JavacElementRefBase ref = refCollector.asJavacRef(superclass);
        if (ref == null) return null;
        supers[interfaces.size()] = ref;
      }
      else {
        supers = interfaces.isEmpty() ? JavacRef.EMPTY_ARRAY : new JavacRef[interfaces.size()];
      }

      int i = 0;
      for (TypeMirror anInterface : interfaces) {
        final JavacRef.JavacElementRefBase ref = refCollector.asJavacRef(anInterface);
        if (ref == null) return null;
        supers[i++] = ref;
      }
      final JavacRef.JavacElementRefBase aClass = refCollector.asJavacRef(element);
      if (aClass == null) return null;
      refCollector.sinkReference(aClass);
      refCollector.sinkDeclaration(new JavacDef.JavacClassDef(aClass, supers));
      super.visitClass(node, refCollector);
    } finally {
      myCurrentEnclosingElement.pop();
      myCurrentEnclosingElementOffset.pop();
    }
    return null;
  }

  static JavacTreeRefScanner createASTScanner() {
    try {
      Class aClass = Class.forName("org.jetbrains.jps.javac.ast.Javac8RefScanner");
      return (JavacTreeRefScanner) aClass.newInstance();
    }
    catch (Throwable ignored) {
      return new JavacTreeRefScanner();
    }
  }

  private static boolean isStatic(Element element) {
    return element.getModifiers().contains(Modifier.STATIC);
  }

  private static TypeElement findQualifier(Element method, TypeElement scopeClass) {
    Element containingClass = method.getEnclosingElement();
    if (containingClass == null) return null;

    while (scopeClass != null) {
      Element parent = getClassOrPackageParent(scopeClass);
      if (scopeClass.getModifiers().contains(Modifier.STATIC) ||
          parent instanceof PackageElement ||
          isInheritorOrSelf(scopeClass, (TypeElement)containingClass)) {
        return scopeClass;
      }
      if (isPackageOrNull(parent)) {
        return null;
      }
      scopeClass = (TypeElement) parent;
    }

    return null;
  }

  private static boolean isIterator(TypeElement aClass, JavacReferenceIndexListener.ReferenceCollector collector) {
    JavacNameTable table = collector.getNameTable();
    TypeElement iterable = table.getIterableElement();
    if (iterable != null && isInheritorOrSelf(aClass, iterable)) {
      return true;
    }
    TypeElement stream = table.getStreamElement();
    if (stream != null && isInheritorOrSelf(aClass, stream)) {
      return true;
    }
    TypeElement iterator = table.getIteratorElement();
    if (iterator != null && isInheritorOrSelf(aClass, iterator)) {
      return true;
    }
    return false;
  }

  private static Element getClassOrPackageParent(Element element) {
    element = element.getEnclosingElement();
    while (element != null) {
      ElementKind kind = element.getKind();
      if (kind == ElementKind.CLASS ||
          kind == ElementKind.INTERFACE ||
          kind == ElementKind.ENUM ||
          kind == ElementKind.PACKAGE) {
        return element;
      }
      element = element.getEnclosingElement();
    }
    return null;
  }

  private static boolean isPackageOrNull(Element element) {
    return element == null || element.getKind() == ElementKind.PACKAGE;
  }

  private static boolean isInheritorOrSelf(TypeElement aClass, TypeElement baseClass) {
    if (aClass == baseClass) return true;

    TypeMirror superType = aClass.getSuperclass();
    if (isTypeCorrespondsToElement(superType, baseClass)) {
      return true;
    }

    List<? extends TypeMirror> interfaces = aClass.getInterfaces();
    for (TypeMirror type : interfaces) {
      if (isTypeCorrespondsToElement(type, baseClass)) {
        return true;
      }
    }

    if (isInheritorOrSelf(superType, baseClass)) return true;

    for (TypeMirror type : interfaces) {
      if (isInheritorOrSelf(type, baseClass)) {
        return true;
      }
    }

    return false;
  }

  private static boolean isInheritorOrSelf(TypeMirror classType, TypeElement baseClass) {
    if (classType != null && classType.getKind() != TypeKind.NONE) {
      return isInheritorOrSelf((TypeElement)((DeclaredType) classType).asElement(), baseClass);
    }
    return false;
  }

  private static boolean isTypeCorrespondsToElement(TypeMirror type, TypeElement baseClass) {
    if (type != null && type.getKind() != TypeKind.NONE) {
      DeclaredType superClass = (DeclaredType)type;
      Element superClassElement = superClass.asElement();
      if (superClassElement == baseClass) return true;
    }
    return false;
  }
}
