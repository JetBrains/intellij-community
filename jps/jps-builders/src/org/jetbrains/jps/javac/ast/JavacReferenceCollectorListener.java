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

import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.ClientCodeException;
import com.sun.tools.javac.util.Name;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.jps.javac.ast.api.JavacFileReferencesRegistrar;
import org.jetbrains.jps.javac.ast.api.JavacRefSymbol;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

final class JavacReferenceCollectorListener implements TaskListener {
  private final JavacFileReferencesRegistrar[] myFullASTListeners;
  private final JavacFileReferencesRegistrar[] myOnlyImportsListeners;
  private final JavacTreeRefScanner myAstScanner;

  private Name myAsterisk;

  private final Map<String, IncompletelyProcessedFile> myIncompletelyProcessedFiles = new THashMap<String, IncompletelyProcessedFile>(10);

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
        final JCTree.JCCompilationUnit unit = (JCTree.JCCompilationUnit)e.getCompilationUnit();
        final String fileName = e.getSourceFile().getName();
        final List<JCTree> declarations = unit.getTypeDecls();
        final int size = declarations.size();

        boolean isFileDataComplete = true;
        boolean submitImportsOnlyData = true;
        JCTree declarationToProcess = null;
        final Set<JavacRefSymbol> collectedReferences;
        final List<JavacRefSymbol> collectedDefinitions;
        final TypeElement analyzedElement = e.getTypeElement();

        switch (size) {
          case 0:
            collectedReferences = IncompletelyProcessedFile.createReferenceHolder();
            collectedDefinitions = IncompletelyProcessedFile.createDefinitionHolder();
            break;
          case 1:
            collectedReferences = IncompletelyProcessedFile.createReferenceHolder();
            collectedDefinitions = IncompletelyProcessedFile.createDefinitionHolder();
            declarationToProcess = declarations.get(0);
            break;
          default:
            IncompletelyProcessedFile incompletelyProcessedFile = myIncompletelyProcessedFiles.get(fileName);
            if (incompletelyProcessedFile == null) {
              myIncompletelyProcessedFiles.put(fileName, incompletelyProcessedFile = new IncompletelyProcessedFile(size));
            } else {
              submitImportsOnlyData = false;
            }

            if (--incompletelyProcessedFile.remainDeclarations == 0) {
              myIncompletelyProcessedFiles.remove(fileName);
            } else {
              isFileDataComplete = false;
            }
            collectedReferences = incompletelyProcessedFile.collectedReferences;
            collectedDefinitions = incompletelyProcessedFile.collectedDefinitions;

            for (JCTree declaration : declarations) {
              if (declaration.type != null && declaration.type.tsym == analyzedElement) {
                declarationToProcess = declaration;
                break;
              }
            }

            if (declarationToProcess == null) throw new IllegalStateException("Can't find tree for " + analyzedElement.getQualifiedName());
        }

        if (submitImportsOnlyData) {
          scanImports(unit, collectedReferences);
          for (JavacFileReferencesRegistrar r : myOnlyImportsListeners) {
            r.registerFile(fileName, collectedReferences, collectedDefinitions);
          }
        }
        if (myFullASTListeners.length == 0) return;
        JavacTreeScannerSink sink = new JavacTreeScannerSink() {
          @Override
          public void sinkReference(JavacRefSymbol ref) {
            collectedReferences.add(ref);
          }

          @Override
          public void sinkDeclaration(JavacRefSymbol def) {
            collectedDefinitions.add(def);
          }
        };
        myAstScanner.scan(declarationToProcess, sink);

        if (isFileDataComplete) {
          for (JCTree.JCAnnotation annotation : unit.getPackageAnnotations()) {
            myAstScanner.scan(annotation, sink);
          }

          for (JavacFileReferencesRegistrar r : myFullASTListeners) {
            r.registerFile(e.getSourceFile().getName(), collectedReferences, collectedDefinitions);
          }
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

  private static class IncompletelyProcessedFile {
    private final Set<JavacRefSymbol> collectedReferences = createReferenceHolder();
    private final List<JavacRefSymbol> collectedDefinitions = createDefinitionHolder();
    private int remainDeclarations;

    private IncompletelyProcessedFile(int remainDeclarations) {
      this.remainDeclarations = remainDeclarations;
    }

    private static Set<JavacRefSymbol> createReferenceHolder() {
      return new THashSet<JavacRefSymbol>();
    }

    private static List<JavacRefSymbol> createDefinitionHolder() {
      return new ArrayList<JavacRefSymbol>();
    }
  }
}
