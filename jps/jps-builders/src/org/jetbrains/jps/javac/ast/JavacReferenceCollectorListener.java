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

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.Consumer;
import com.intellij.util.ReflectionUtil;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.tools.javac.util.ClientCodeException;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.javac.ast.api.JavacDef;
import org.jetbrains.jps.javac.ast.api.JavacFileData;
import org.jetbrains.jps.javac.ast.api.JavacRef;
import org.jetbrains.jps.javac.ast.api.JavacNameTable;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

final class JavacReferenceCollectorListener implements TaskListener {
  private final boolean myDivideImportRefs;
  private final Consumer<JavacFileData> myDataConsumer;
  private final JavacTreeRefScanner myAstScanner;
  private final Elements myElementUtility;
  private final Types myTypeUtility;
  private final Trees myTreeUtility;
  private final JavacNameTable myNameTableCache;

  private NotNullLazyValue<Name> myAsterisk = new NotNullLazyValue<Name>() {
    @NotNull
    @Override
    protected Name compute() {
      return myElementUtility.getName("*");
    }
  };

  private final Map<String, ReferenceCollector> myIncompletelyProcessedFiles = new THashMap<String, ReferenceCollector>(10);

  static void installOn(JavaCompiler.CompilationTask task,
                        boolean divideImportRefs,
                        Consumer<JavacFileData> dataConsumer) {
    JavacTask javacTask = (JavacTask)task;
    Method addTaskMethod = ReflectionUtil.getMethod(JavacTask.class, "addTaskListener", TaskListener.class); // jdk >= 8
    final JavacReferenceCollectorListener taskListener = new JavacReferenceCollectorListener(divideImportRefs,
                                                                                             dataConsumer,
                                                                                             javacTask.getElements(),
                                                                                             javacTask.getTypes(),
                                                                                             Trees.instance(javacTask));
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

  private JavacReferenceCollectorListener(boolean divideImportRefs,
                                          Consumer<JavacFileData> dataConsumer,
                                          Elements elementUtility,
                                          Types typeUtility,
                                          Trees treeUtility) {
    myDivideImportRefs = divideImportRefs;
    myDataConsumer = dataConsumer;
    myElementUtility = elementUtility;
    myTypeUtility = typeUtility;
    myTreeUtility = treeUtility;
    myAstScanner = JavacTreeRefScanner.createASTScanner();
    myNameTableCache = new JavacNameTable(elementUtility);
  }

  @Override
  public void started(TaskEvent e) {

  }

  @Override
  public void finished(TaskEvent e) {
    try {
      if (e.getKind() == TaskEvent.Kind.ANALYZE) {
        // javac creates an event on each processed top level declared class not file
        final CompilationUnitTree unit = e.getCompilationUnit();
        final String fileName = e.getSourceFile().getName();

        Tree declarationToProcess = myTreeUtility.getTree(e.getTypeElement());

        boolean collectImportsData;
        boolean addedToCache = true;
        ReferenceCollector incompletelyProcessedFile = myIncompletelyProcessedFiles.get(fileName);
        if (incompletelyProcessedFile == null) {
          final int declarationCount = unit.getTypeDecls().size();
          incompletelyProcessedFile = new ReferenceCollector(declarationCount, fileName, unit);
          if (declarationCount == 1 && declarationToProcess != null) {
            addedToCache = false;
          } else {
            myIncompletelyProcessedFiles.put(fileName, incompletelyProcessedFile);
          }
          collectImportsData = true;
        }
        else {
          collectImportsData = false;
        }

        final boolean isFileDataComplete;
        if (incompletelyProcessedFile.decrementRemainDeclarationsAndGet(declarationToProcess) == 0) {
          if (addedToCache) {
            myIncompletelyProcessedFiles.remove(fileName);
          }
          isFileDataComplete = true;
        }
        else {
          isFileDataComplete = false;
        }

        if (collectImportsData) {
          scanImports(unit, incompletelyProcessedFile.myFileData.getRefs(), incompletelyProcessedFile);
          if (myDivideImportRefs) {
            scanImports(unit, incompletelyProcessedFile.myFileData.getImportRefs(), incompletelyProcessedFile);
          }
        }
        myAstScanner.scan(declarationToProcess, incompletelyProcessedFile);

        if (isFileDataComplete) {
          for (AnnotationTree annotation : unit.getPackageAnnotations()) {
            myAstScanner.scan(annotation, incompletelyProcessedFile);
          }

          myDataConsumer.consume(incompletelyProcessedFile.myFileData);
        }
      }
    }
    catch (Exception ex) {
      throw new ClientCodeException(ex);
    }
  }

  private void scanImports(CompilationUnitTree compilationUnit,
                           Collection<JavacRef> elements,
                           ReferenceCollector incompletelyProcessedFile) {
    for (ImportTree anImport : compilationUnit.getImports()) {
      final MemberSelectTree id = (MemberSelectTree)anImport.getQualifiedIdentifier();
      final Element element = incompletelyProcessedFile.getReferencedElement(id);
      if (element == null) {
        final ExpressionTree qExpr = id.getExpression();
        if (qExpr instanceof MemberSelectTree) {
          final MemberSelectTree classImport = (MemberSelectTree)qExpr;
          final Element ownerElement = incompletelyProcessedFile.getReferencedElement(classImport);
          final Name name = id.getIdentifier();
          if (name != myAsterisk.getValue()) {
            // member import
            for (Element memberElement : myElementUtility.getAllMembers((TypeElement)ownerElement)) {
              if (memberElement.getSimpleName() == name) {
                elements.add(JavacRef.JavacElementRefBase.fromElement(memberElement, myNameTableCache));
              }
            }
          }
          collectClassImports(ownerElement, elements);
        }
      } else {
        // class import
        collectClassImports(element, elements);
      }
    }
  }

  private void collectClassImports(Element baseImport, Collection<JavacRef> collector) {
    for (Element element = baseImport;
         element != null && element.getKind() != ElementKind.PACKAGE;
         element = element.getEnclosingElement()) {
      collector.add(JavacRef.JavacElementRefBase.fromElement(element, myNameTableCache));
    }
  }

  class ReferenceCollector {
    private final JavacFileData myFileData;
    private final JavacTreeHelper myTreeHelper;
    private int myRemainDeclarations;

    private ReferenceCollector(int remainDeclarations,
                               String filePath,
                               CompilationUnitTree unitTree) {
      myRemainDeclarations = remainDeclarations;
      myFileData = new JavacFileData(filePath,
                                     createReferenceHolder(),
                                     myDivideImportRefs ? createReferenceHolder() : Collections.<JavacRef>emptyList(),
                                     createDefinitionHolder());
      myTreeHelper = new JavacTreeHelper(unitTree, myTreeUtility);
    }

    void sinkReference(JavacRef.JavacElementRefBase ref) {
      myFileData.getRefs().add(ref);
    }

    void sinkDeclaration(JavacDef def) {
     myFileData.getDefs().add(def);
    }

    JavacRef.JavacElementRefBase asJavacRef(Element element) {
      return JavacRef.JavacElementRefBase.fromElement(element, myNameTableCache);
    }

    Element getReferencedElement(Tree tree) {
      return myTreeHelper.getReferencedElement(tree);
    }

    TypeMirror getType(Tree tree) {
      return myTreeHelper.getType(tree);
    }

    Types getTypeUtility() {
      return myTypeUtility;
    }

    private int decrementRemainDeclarationsAndGet(Tree declarationToProcess) {
      return declarationToProcess == null ? myRemainDeclarations : --myRemainDeclarations;
    }
  }

  private static Set<JavacRef> createReferenceHolder() {
    return new THashSet<JavacRef>(new TObjectHashingStrategy<JavacRef>() {
      @Override
      public int computeHashCode(JavacRef ref) {
        return ((JavacRef.JavacElementRefBase) ref).getOriginalElement().hashCode();
      }

      @Override
      public boolean equals(JavacRef r1, JavacRef r2) {
        return ((JavacRef.JavacElementRefBase) r1).getOriginalElement() == ((JavacRef.JavacElementRefBase) r2).getOriginalElement();
      }
    });
  }

  private static List<JavacDef> createDefinitionHolder() {
    return new ArrayList<JavacDef>();
  }

  private static class JavacTreeHelper {
    private final TreePath myUnitPath;
    private final Trees myTreeUtil;

    private JavacTreeHelper(CompilationUnitTree unit, Trees treeUtil) {
      myUnitPath = new TreePath(unit);
      myTreeUtil = treeUtil;
    }

    private Element getReferencedElement(Tree tree) {
      return myTreeUtil.getElement(new TreePath(myUnitPath, tree));
    }

    public TypeMirror getType(Tree tree) {
      return myTreeUtil.getTypeMirror(new TreePath(myUnitPath, tree));
    }
  }
}
