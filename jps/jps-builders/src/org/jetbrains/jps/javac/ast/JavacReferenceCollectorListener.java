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

import com.intellij.util.Consumer;
import com.intellij.util.ReflectionUtil;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.ClientCodeException;
import com.sun.tools.javac.util.Name;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacFileData;
import org.jetbrains.jps.javac.ast.api.JavacRef;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

final class JavacReferenceCollectorListener implements TaskListener {
  private final boolean myDivideImportRefs;
  private final Consumer<JavacFileData> myDataConsumer;
  private final JavacTreeRefScanner myAstScanner;

  private Name myAsterisk;

  private final Map<String, IncompletelyProcessedFile> myIncompletelyProcessedFiles = new THashMap<String, IncompletelyProcessedFile>(10);

  static void installOn(JavaCompiler.CompilationTask task,
                        boolean divideImportRefs,
                        Consumer<JavacFileData> dataConsumer) {
    JavacTask javacTask = (JavacTask)task;
    Method addTaskMethod = ReflectionUtil.getMethod(JavacTask.class, "addTaskListener", TaskListener.class); // jdk >= 8
    final JavacReferenceCollectorListener taskListener = new JavacReferenceCollectorListener(divideImportRefs, dataConsumer);
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

  private JavacReferenceCollectorListener(boolean divideImportRefs, Consumer<JavacFileData> dataConsumer) {
    myDivideImportRefs = divideImportRefs;
    myDataConsumer = dataConsumer;
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
        boolean collectImportsData = true;
        JCTree declarationToProcess = null;
        final TypeElement analyzedElement = e.getTypeElement();

        IncompletelyProcessedFile incompletelyProcessedFile;
        switch (size) {
          case 0:
            incompletelyProcessedFile = new IncompletelyProcessedFile(0, fileName);
            break;
          case 1:
            incompletelyProcessedFile = new IncompletelyProcessedFile(0, fileName);
            declarationToProcess = declarations.get(0);
            break;
          default:
            incompletelyProcessedFile = myIncompletelyProcessedFiles.get(fileName);
            if (incompletelyProcessedFile == null) {
              myIncompletelyProcessedFiles.put(fileName, incompletelyProcessedFile = new IncompletelyProcessedFile(size, fileName));
            } else {
              collectImportsData = false;
            }

            if (incompletelyProcessedFile.decrementRemainDeclarationsAndGet() == 0) {
              myIncompletelyProcessedFiles.remove(fileName);
            } else {
              isFileDataComplete = false;
            }

            for (JCTree declaration : declarations) {
              if (declaration.type != null && declaration.type.tsym == analyzedElement) {
                declarationToProcess = declaration;
                break;
              }
            }

            if (declarationToProcess == null) throw new IllegalStateException("Can't find tree for " + analyzedElement.getQualifiedName());
        }

        if (collectImportsData) {
          scanImports(unit, incompletelyProcessedFile.myFileData.getRefs());
          if (myDivideImportRefs) {
            scanImports(unit, incompletelyProcessedFile.myFileData.getImportRefs());
          }
        }
        final IncompletelyProcessedFile finalIncompletelyProcessedFile = incompletelyProcessedFile;
        JavacTreeScannerSink sink = new JavacTreeScannerSink() {
          @Override
          public void sinkReference(JavacRef.JavacSymbolRefBase ref) {
            finalIncompletelyProcessedFile.myFileData.getRefs().add(ref);
          }

          @Override
          public void sinkDeclaration(JavacDef def) {
            finalIncompletelyProcessedFile.myFileData.getDefs().add(def);
          }
        };
        myAstScanner.scan(declarationToProcess, sink);

        if (isFileDataComplete) {
          for (JCTree.JCAnnotation annotation : unit.getPackageAnnotations()) {
            myAstScanner.scan(annotation, sink);
          }

          myDataConsumer.consume(incompletelyProcessedFile.myFileData);
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

  private void scanImports(JCTree.JCCompilationUnit compilationUnit, Collection<JavacRef> symbols) {
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
                symbols.add(JavacRef.JavacSymbolRefBase.fromSymbol(memberSymbol));
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

  private static void collectClassImports(Symbol baseImport, Collection<JavacRef> collector) {
    for (Symbol symbol = baseImport;
         symbol != null && symbol.getKind() != ElementKind.PACKAGE;
         symbol = symbol.owner) {
      collector.add(JavacRef.JavacSymbolRefBase.fromSymbol(symbol));
    }
  }

  private class IncompletelyProcessedFile {
    private final JavacFileData myFileData;
    private int myRemainDeclarations;

    private IncompletelyProcessedFile(int remainDeclarations, String filePath) {
      myRemainDeclarations = remainDeclarations;
      myFileData = new JavacFileData(filePath,
                                     createReferenceHolder(),
                                     myDivideImportRefs ? createReferenceHolder() : Collections.<JavacRef>emptyList(),
                                     createDefinitionHolder());
    }

    private int decrementRemainDeclarationsAndGet() {
      return --myRemainDeclarations;
    }
  }

  private static Set<JavacRef> createReferenceHolder() {
    return new THashSet<JavacRef>(new TObjectHashingStrategy<JavacRef>() {
      @Override
      public int computeHashCode(JavacRef ref) {
        return ((JavacRef.JavacSymbolRefBase) ref).getOriginalElement().hashCode();
      }

      @Override
      public boolean equals(JavacRef r1, JavacRef r2) {
        return ((JavacRef.JavacSymbolRefBase) r1).getOriginalElement() == ((JavacRef.JavacSymbolRefBase) r2).getOriginalElement();
      }
    });
  }

  private static List<JavacDef> createDefinitionHolder() {
    return new ArrayList<JavacDef>();
  }
}
