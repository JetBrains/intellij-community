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

import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacRef;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

class JavacTreeRefScanner extends TreeScanner<Tree, JavacTreeScannerSink> {
  private static final Set<ElementKind> ALLOWED_ELEMENTS = EnumSet.of(ElementKind.ENUM,
                                                                      ElementKind.CLASS,
                                                                      ElementKind.ANNOTATION_TYPE,
                                                                      ElementKind.INTERFACE,
                                                                      ElementKind.ENUM_CONSTANT,
                                                                      ElementKind.FIELD,
                                                                      ElementKind.CONSTRUCTOR,
                                                                      ElementKind.METHOD);

  @Override
  public Tree visitCompilationUnit(CompilationUnitTree node, JavacTreeScannerSink sink) {
    scan(node.getPackageAnnotations(), sink);
    scan(node.getTypeDecls(), sink);
    return node;
  }

  @Override
  public Tree visitIdentifier(IdentifierTree node, JavacTreeScannerSink sink) {
    final Element element = sink.getReferencedElement(node);
    if (element == null) {
      return null;
    }
    if (ALLOWED_ELEMENTS.contains(element.getKind())) {
      sink.sinkReference(sink.asJavacRef(element));
    }
    return null;
  }

  @Override
  public Tree visitVariable(VariableTree node, JavacTreeScannerSink sink) {
    final Element element = sink.getReferencedElement(node);
    if (element != null && element.getKind() == ElementKind.FIELD) {
      sink.sinkReference(sink.asJavacRef(element));
    }
    return super.visitVariable(node, sink);
  }

  @Override
  public Tree visitMemberSelect(MemberSelectTree node, JavacTreeScannerSink sink) {
    final Element element = sink.getReferencedElement(node);
    if (element != null && element.getKind() != ElementKind.PACKAGE) {
      sink.sinkReference(sink.asJavacRef(element));
    }
    return super.visitMemberSelect(node, sink);
  }

  @Override
  public Tree visitMethod(MethodTree node, JavacTreeScannerSink sink) {
    final Element element = sink.getReferencedElement(node);
    if (element != null) {
      sink.sinkReference(sink.asJavacRef(element));
    }
    return super.visitMethod(node, sink);
  }
  
  
  @Override
  public Tree visitClass(ClassTree node, JavacTreeScannerSink sink) {
    TypeElement element = (TypeElement)sink.getReferencedElement(node);
    if (element == null) return null;

    final TypeMirror superclass = element.getSuperclass();
    final List<? extends TypeMirror> interfaces = element.getInterfaces();
    final JavacRef[] supers;
    if (superclass != sink.getTypeUtility().getNoType(TypeKind.NONE)) {
      supers = new JavacRef[interfaces.size() + 1];
      supers[interfaces.size()] = sink.asJavacRef(sink.getTypeUtility().asElement(superclass));

    } else {
      supers = interfaces.isEmpty() ? JavacRef.EMPTY_ARRAY : new JavacRef[interfaces.size()];
    }

    int i = 0;
    for (TypeMirror anInterface : interfaces) {
      supers[i++] = sink.asJavacRef(sink.getTypeUtility().asElement(anInterface));

    }
    final JavacRef.JavacElementRefBase aClass = sink.asJavacRef(element);

    sink.sinkReference(aClass);
    sink.sinkDeclaration(new JavacDef.JavacClassDef(aClass, supers));
    return super.visitClass(node, sink);
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
}
