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

import com.intellij.util.ReflectionUtil;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.ClientCodeException;
import com.sun.tools.javac.util.Name;
import gnu.trove.THashSet;
import org.jetbrains.jps.javac.ast.api.JavacFileReferencesRegistrar;
import org.jetbrains.jps.javac.ast.api.JavacRefSymbol;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class JavacReferenceCollectorListener implements TaskListener {
  private final JavacFileReferencesRegistrar[] myFullASTListeners;
  private final JavacFileReferencesRegistrar[] myOnlyImportsListeners;
  private final JavacTreeRefScanner myAstScanner;

  private Name myAsterisk;

  private int myRemainDeclarations;
  private JCTree.JCCompilationUnit myCurrentCompilationUnit;
  private Set<JavacRefSymbol> myCollectedReferences;
  private List<JavacRefSymbol> myCollectedDefinitions;

  static void installOn(JavaCompiler.CompilationTask task,
                        JavacFileReferencesRegistrar[] fullASTListenerArray,
                        JavacFileReferencesRegistrar[] onlyImportsListenerArray) {
    JavacTask javacTask = (JavacTask)task;
    Method addTaskMethod = ReflectionUtil.getMethod(JavacTask.class, "addTaskListener", TaskListener.class); // jdk >= 8
    final JavacReferenceCollectorListener taskListener = new JavacReferenceCollectorListener(fullASTListenerArray, onlyImportsListenerArray);
    if (addTaskMethod != null) {
      try {
        addTaskMethod.invoke(task, taskListener);
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      catch (InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    } else {
      // jdk 6-7
      javacTask.setTaskListener(taskListener);
    }
  }

  private JavacReferenceCollectorListener(JavacFileReferencesRegistrar[] fullASTListenerArray, JavacFileReferencesRegistrar[] importsListenerArray) {
    myFullASTListeners = fullASTListenerArray;
    myOnlyImportsListeners = importsListenerArray;
    myAstScanner = JavacTreeRefScanner.createASTScanner();
  }

  @Override
  public void started(TaskEvent e) {

  }

  @Override
  public void finished(TaskEvent e) {
    try {
      if (e.getKind() == TaskEvent.Kind.ANALYZE) {
        // javac creates an event on each processed top level declared class not file
        if (myCurrentCompilationUnit != e.getCompilationUnit()) {
          myCurrentCompilationUnit = (JCTree.JCCompilationUnit)e.getCompilationUnit();
          myCollectedDefinitions = new ArrayList<JavacRefSymbol>();
          myCollectedReferences = new THashSet<JavacRefSymbol>();
          myRemainDeclarations = myCurrentCompilationUnit.getTypeDecls().size() - 1;
          scanImports(myCurrentCompilationUnit, myCollectedReferences);
          for(JavacFileReferencesRegistrar r: myOnlyImportsListeners) {
            r.registerFile(e.getSourceFile().getName(), myCollectedReferences, myCollectedDefinitions);
          }
        }
        else {
          myRemainDeclarations--;
        }

        JavacTreeScannerSink sink = new JavacTreeScannerSink() {
          @Override
          public void sinkReference(JavacRefSymbol ref) {
            myCollectedReferences.add(ref);
          }

          @Override
          public void sinkDeclaration(JavacRefSymbol def) {
            myCollectedDefinitions.add(def);
          }
        };

        if (myFullASTListeners.length != 0) {
          TypeElement analyzedElement = e.getTypeElement();
          for (JCTree tree : myCurrentCompilationUnit.getTypeDecls()) {
            if (tree.type != null && tree.type.tsym == analyzedElement) {
              myAstScanner.scan(tree, sink);
            }
          }
        }

        if (myRemainDeclarations == 0) {
          if (myFullASTListeners.length != 0) {
            for (JCTree.JCAnnotation annotation : myCurrentCompilationUnit.getPackageAnnotations()) {
              myAstScanner.scan(annotation, sink);
            }
          }

          for(JavacFileReferencesRegistrar r: myFullASTListeners) {
            r.registerFile(e.getSourceFile().getName(), myCollectedReferences, myCollectedDefinitions);
          }

          myCurrentCompilationUnit = null;
          myCollectedDefinitions = null;
          myCollectedReferences = null;
        }

      }
    }
    catch (Exception ex) {
      throw new ClientCodeException(ex);
    }
  }

  private Name getAsteriskFromCurrentNameTable(Name tableRepresentative) {
    if (myAsterisk == null) {
      myAsterisk = tableRepresentative.table.fromChars(new char[]{'*'}, 0, 1);
    }
    return myAsterisk;
  }

  private void scanImports(JCTree.JCCompilationUnit compilationUnit, Set<JavacRefSymbol> symbols) {
    for (JCTree.JCImport anImport : compilationUnit.getImports()) {
      final JCTree.JCFieldAccess id = (JCTree.JCFieldAccess)anImport.getQualifiedIdentifier();
      final Symbol sym = id.sym;
      if (sym == null) {
        final JCTree.JCExpression qExpr = id.getExpression();
        if (qExpr instanceof JCTree.JCFieldAccess) {
          final JCTree.JCFieldAccess classImport = (JCTree.JCFieldAccess)qExpr;
          final Symbol ownerSym = classImport.sym;
          final Name name = id.getIdentifier();
          if (name != getAsteriskFromCurrentNameTable(name)) {
            // member import
            for (Symbol memberSymbol : ownerSym.members().getElements()) {
              if (memberSymbol.getSimpleName() == name) {
                symbols.add(new JavacRefSymbol(memberSymbol, Tree.Kind.IMPORT));
              }
            }
          }
          collectClassImports(ownerSym, symbols);
        }
      } else {
        // class import
        collectClassImports(sym, symbols);
      }
    }
  }

  private static void collectClassImports(Symbol baseImport, Set<JavacRefSymbol> collector) {
    for (Symbol symbol = baseImport;
         symbol != null && symbol.getKind() != ElementKind.PACKAGE;
         symbol = symbol.owner) {
      collector.add(new JavacRefSymbol(symbol, Tree.Kind.IMPORT));
    }
  }
}
