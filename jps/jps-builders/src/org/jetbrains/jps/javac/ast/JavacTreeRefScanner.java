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
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacRef;

import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeKind;

class JavacTreeRefScanner extends TreeScanner<Tree, JavacTreeScannerSink> {
  @Override
  public Tree visitCompilationUnit(CompilationUnitTree node, JavacTreeScannerSink sink) {
    scan(node.getPackageAnnotations(), sink);
    scan(node.getTypeDecls(), sink);
    return node;
  }

  @Override
  public Tree visitIdentifier(IdentifierTree node, JavacTreeScannerSink sink) {
    final JCTree.JCIdent javacIdentifier = (JCTree.JCIdent)node;
    final Type type = javacIdentifier.type;
    if (type == null) {
      return null;
    }
    if (type.getKind() == TypeKind.PACKAGE) {
      return null;
    }
    final Symbol sym = javacIdentifier.sym;
    if (sym.getKind() == ElementKind.PARAMETER ||
        sym.getKind() == ElementKind.LOCAL_VARIABLE ||
        sym.getKind() == ElementKind.EXCEPTION_PARAMETER ||
        sym.getKind() == ElementKind.TYPE_PARAMETER) {
      return null;
    }
    sink.sinkReference(JavacRef.JavacSymbolRefBase.fromSymbol(sym));
    return null;
  }

  @Override
  public Tree visitVariable(VariableTree node, JavacTreeScannerSink sink) {
    final Symbol.VarSymbol sym = ((JCTree.JCVariableDecl)node).sym;
    if (sym.getKind() == ElementKind.FIELD) {
      sink.sinkReference(JavacRef.JavacSymbolRefBase.fromSymbol(sym));
    }
    return super.visitVariable(node, sink);
  }

  @Override
  public Tree visitMemberSelect(MemberSelectTree node, JavacTreeScannerSink sink) {
    final Symbol sym = ((JCTree.JCFieldAccess)node).sym;
    if (sym.getKind() != ElementKind.PACKAGE) {
      sink.sinkReference(JavacRef.JavacSymbolRefBase.fromSymbol(sym));
    }
    return super.visitMemberSelect(node, sink);
  }

  @Override
  public Tree visitMethod(MethodTree node, JavacTreeScannerSink sink) {
    final Symbol.MethodSymbol sym = ((JCTree.JCMethodDecl)node).sym;
    sink.sinkReference(JavacRef.JavacSymbolRefBase.fromSymbol(sym));
    return super.visitMethod(node, sink);
  }
  
  
  @Override
  public Tree visitClass(ClassTree node, JavacTreeScannerSink sink) {
    JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl)node;
    Symbol.ClassSymbol sym = classDecl.sym;

    final Type superclass = sym.getSuperclass();
    final List<Type> interfaces = sym.getInterfaces();
    final JavacRef[] supers;
    if (superclass != Type.noType) {
      supers = new JavacRef[interfaces.size() + 1];
      supers[interfaces.size()] = JavacRef.JavacSymbolRefBase.fromSymbol(superclass.asElement());
    } else {
      supers = interfaces.isEmpty() ? JavacRef.EMPTY_ARRAY : new JavacRef[interfaces.size()];
    }

    int i = 0;
    for (Type anInterface : interfaces) {
      supers[i++] = JavacRef.JavacSymbolRefBase.fromSymbol(anInterface.asElement());
    }
    final JavacRef.JavacSymbolRefBase aClass = JavacRef.JavacSymbolRefBase.fromSymbol(sym);

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
