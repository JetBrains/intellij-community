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
import com.intellij.util.SmartList;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.ClientCodeException;
import com.sun.tools.javac.util.Name;
import gnu.trove.THashSet;
import org.jetbrains.jps.javac.ast.api.JavacDefSymbol;
import org.jetbrains.jps.javac.ast.api.JavacFileReferencesRegistrar;
import org.jetbrains.jps.javac.ast.api.JavacRefSymbol;
import org.jetbrains.jps.service.JpsServiceManager;

import javax.lang.model.element.ElementKind;
import javax.tools.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class JavacReferencesCollector {
  public static void installOn(JavacTask task) {
    List<JavacFileReferencesRegistrar> fullASTListeners = new SmartList<JavacFileReferencesRegistrar>();
    List<JavacFileReferencesRegistrar> onlyImportsListeners = new SmartList<JavacFileReferencesRegistrar>();
    for (JavacFileReferencesRegistrar listener : JpsServiceManager.getInstance().getExtensions(JavacFileReferencesRegistrar.class)) {
      if (!listener.initialize()) {
        continue;
      }
      (listener.onlyImports() ? onlyImportsListeners : fullASTListeners).add(listener);
    }

    final JavacFileReferencesRegistrar[] fullASTListenerArray = fullASTListeners.toArray(new JavacFileReferencesRegistrar[fullASTListeners.size()]);
    final JavacFileReferencesRegistrar[] onlyImportsListenerArray = onlyImportsListeners.toArray(new JavacFileReferencesRegistrar[onlyImportsListeners.size()]);
    if (fullASTListenerArray.length == 0 && onlyImportsListenerArray.length == 0) {
      return;
    }

    Method addTaskMethod = ReflectionUtil.getMethod(JavacTask.class, "addTaskListener", TaskListener.class); // jdk >= 8
    if (addTaskMethod == null) {
      addTaskMethod = ReflectionUtil.getMethod(JavacTask.class, "setTaskListener", TaskListener.class); // jdk 6-7
    }
    assert addTaskMethod != null;

    try {
      addTaskMethod.invoke(task, new MyTaskListener(fullASTListenerArray, onlyImportsListenerArray));
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private static final class MyTaskListener implements TaskListener {
    private JCTree.JCCompilationUnit myCurrentCompilationUnit;
    private final JavacFileReferencesRegistrar[] myFullASTListeners;
    private final JavacFileReferencesRegistrar[] myOnlyImportsListeners;
    private final JavacTreeRefScanner myAstScanner;

    private int myCurrentSize;
    private Name myAsteriks;

    public MyTaskListener(JavacFileReferencesRegistrar[] fullASTListenerArray, JavacFileReferencesRegistrar[] importsListenerArray) {
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
          // javac creates event on each processed class not file
          if (myCurrentCompilationUnit != e.getCompilationUnit()) {
            myCurrentCompilationUnit = (JCTree.JCCompilationUnit)e.getCompilationUnit();
            myCurrentSize = myCurrentCompilationUnit.getTypeDecls().size() - 1;
          }
          else {
            myCurrentSize--;
          }

          if (myCurrentSize == 0) {
            final JavaFileObject sourceFile = e.getSourceFile();
            final Set<JavacRefSymbol> symbols = new THashSet<JavacRefSymbol>();
            scanImports(myCurrentCompilationUnit, symbols);
            for (JavacFileReferencesRegistrar listener : myOnlyImportsListeners) {
              listener.registerFile(sourceFile, symbols, Collections.<JavacDefSymbol>emptySet());
            }
            if (myFullASTListeners.length != 0) {
              final Collection<JavacDefSymbol> defs = new ArrayList<JavacDefSymbol>();
              myAstScanner.scan(myCurrentCompilationUnit, new JavacTreeScannerSink() {
                @Override
                public void sinkReference(JavacRefSymbol ref) {
                  symbols.add(ref);
                }

                @Override
                public void sinkDeclaration(JavacDefSymbol def) {
                  defs.add(def);
                }
              });
              for (JavacFileReferencesRegistrar listener : myFullASTListeners) {
                listener.registerFile(sourceFile, symbols, defs);
              }
            }
          }
        }
      }
      catch (Exception ex) {
        throw new ClientCodeException(ex);
      }
    }

    private Name getAsteriksFromCurrentNameTable(Name tableRepresentative) {
      if (myAsteriks == null) {
        myAsteriks = tableRepresentative.table.fromChars(new char[]{'*'}, 0, 1);
      }
      return myAsteriks;
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
            if (name != getAsteriksFromCurrentNameTable(name)) {
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
  }

  private static void collectClassImports(Symbol baseImport, Set<JavacRefSymbol> collector) {
    for (Symbol symbol = baseImport;
         symbol != null && symbol.getKind() != ElementKind.PACKAGE;
         symbol = symbol.owner) {
      collector.add(new JavacRefSymbol(symbol, Tree.Kind.IMPORT));
    }
  }
}
