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
import org.jetbrains.jps.javac.ast.api.JavacRefSymbol;

import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeKind;
import java.util.List;

class JavacTreeRefScanner extends TreeScanner<Tree, JavacTreeScannerSink> {
  private static final Symbol[] EMPTY_SYMBOL_ARRAY = new Symbol[0];
  
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
        sym.getKind() == ElementKind.FIELD ||
        sym.getKind() == ElementKind.EXCEPTION_PARAMETER ||
        sym.getKind() == ElementKind.TYPE_PARAMETER) {
      return null;
    }
    sink.sinkReference(new JavacRefSymbol(sym, Tree.Kind.IDENTIFIER));
    return null;
  }

  @Override
  public Tree visitVariable(VariableTree node, JavacTreeScannerSink sink) {
    final Symbol.VarSymbol sym = ((JCTree.JCVariableDecl)node).sym;
    if (sym.getKind() == ElementKind.FIELD) {
      sink.sinkReference(new JavacRefSymbol(sym, Tree.Kind.VARIABLE));
    }
    return super.visitVariable(node, sink);
  }

  @Override
  public Tree visitMemberSelect(MemberSelectTree node, JavacTreeScannerSink sink) {
    final Symbol sym = ((JCTree.JCFieldAccess)node).sym;
    if (sym.getKind() != ElementKind.PACKAGE) {
      sink.sinkReference(new JavacRefSymbol(sym, Tree.Kind.MEMBER_SELECT));
    }
    return super.visitMemberSelect(node, sink);
  }

  @Override
  public Tree visitMethod(MethodTree node, JavacTreeScannerSink sink) {
    final Symbol.MethodSymbol sym = ((JCTree.JCMethodDecl)node).sym;
    sink.sinkReference(new JavacRefSymbol(sym, Tree.Kind.METHOD));
    return super.visitMethod(node, sink);
  }
  
  
  @Override
  public Tree visitClass(ClassTree node, JavacTreeScannerSink sink) {
    Symbol.ClassSymbol sym = ((JCTree.JCClassDecl)node).sym;
    sink.sinkReference(new JavacRefSymbol(sym, Tree.Kind.CLASS));

    Type superclass = sym.getSuperclass();
    List<Type> interfaces = sym.getInterfaces();

    final Symbol[] supers;
    if (superclass != Type.noType) {
      supers = new Symbol[interfaces.size() + 1];
      supers[interfaces.size()] = superclass.asElement();
    } else {
      supers = interfaces.isEmpty() ? EMPTY_SYMBOL_ARRAY : new Symbol[interfaces.size()];
    }

    int i = 0;
    for (Type anInterface : interfaces) {
      supers[i++] = anInterface.asElement();
    }
    sink.sinkClassDeclaration(sym, supers);

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
