/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.tools.javac.util.ClientCodeException;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.javac.ast.api.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.*;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class JavacReferenceCollectorListener implements TaskListener {
  private final static TObjectIntHashMap<JavacRef> EMPTY_T_OBJ_INT_MAP = new TObjectIntHashMap<JavacRef>(0);

  private final boolean myDivideImportRefs;
  private final Consumer<JavacFileData> myDataConsumer;
  private final JavacTask myJavacTask;
  private final JavacTreeRefScanner myAstScanner;
  private final boolean myAtLeastJdk8;

  private boolean myInitialized;
  private Elements myElementUtility;
  private Types myTypeUtility;
  private Trees myTreeUtility;
  private JavacNameTable myNameTableCache;

  private final Map<String, ReferenceCollector> myIncompletelyProcessedFiles = new HashMap<String, ReferenceCollector>(10);

  static void installOn(JavaCompiler.CompilationTask task,
                        boolean divideImportRefs,
                        Consumer<JavacFileData> dataConsumer) {
    JavacTask javacTask = (JavacTask)task;
    Method addTaskMethod; // jdk >= 8
    try {
      addTaskMethod = JavacTask.class.getMethod("addTaskListener", TaskListener.class);
    }
    catch (NoSuchMethodException e) {
      addTaskMethod = null;
    }
    final JavacReferenceCollectorListener taskListener = new JavacReferenceCollectorListener(divideImportRefs,
                                                                                             dataConsumer,
                                                                                             javacTask,
                                                                                             addTaskMethod != null);
    if (addTaskMethod != null) {
      try {
        addTaskMethod.setAccessible(true);
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
                                          JavacTask javacTask,
                                          boolean atLeastJdk8) {
    myDivideImportRefs = divideImportRefs;
    myDataConsumer = dataConsumer;
    myJavacTask = javacTask;
    myAtLeastJdk8 = atLeastJdk8;
    myAstScanner = JavacTreeRefScanner.createASTScanner();
  }

  @Override
  public void started(TaskEvent e) {

  }

  @Override
  public void finished(TaskEvent e) {
    // Should be initialized only when JavaCompiler was created (jdk 6-7).
    // Otherwise JavacReferenceCollectorListener will not be loaded to javac Context.
    initializeUtilitiesIfNeeded();
    try {
      if (e.getKind() == TaskEvent.Kind.ANALYZE) {
        // javac creates an event on each processed top level declared class not file
        final CompilationUnitTree unit = e.getCompilationUnit();
        final String fileName = new File(e.getSourceFile().toUri().getPath()).getPath();

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

  private void initializeUtilitiesIfNeeded() {
    if (!myInitialized) {
      myElementUtility = myJavacTask.getElements();
      myTypeUtility = myJavacTask.getTypes();
      myTreeUtility = Trees.instance(myJavacTask);
      myNameTableCache = new JavacNameTable(myElementUtility);
      myInitialized = true;
    }
  }

  private void scanImports(CompilationUnitTree compilationUnit,
                           TObjectIntHashMap<JavacRef> elements,
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
          if (!myNameTableCache.isAsterisk(name)) {
            // member import
            for (Element memberElement : myElementUtility.getAllMembers((TypeElement)ownerElement)) {
              if (memberElement.getSimpleName() == name) {
                incrementOrAdd(elements, JavacRef.JavacElementRefBase.fromElement(memberElement, null, myNameTableCache));
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

  private void collectClassImports(Element baseImport, TObjectIntHashMap<JavacRef> collector) {
    for (Element element = baseImport;
         element != null && element.getKind() != ElementKind.PACKAGE;
         element = element.getEnclosingElement()) {
      incrementOrAdd(collector, JavacRef.JavacElementRefBase.fromElement(element, null, myNameTableCache));
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
                                     myDivideImportRefs ? createReferenceHolder() : EMPTY_T_OBJ_INT_MAP,
                                     new ArrayList<JavacTypeCast>(),
                                     createDefinitionHolder());
      myTreeHelper = new JavacTreeHelper(unitTree, myTreeUtility);
    }

    void sinkReference(@Nullable JavacRef.JavacElementRefBase ref) {
      incrementOrAdd(myFileData.getRefs(), ref);
    }

    void sinkDeclaration(JavacDef def) {
     myFileData.getDefs().add(def);
    }

    public void sinkTypeCast(JavacTypeCast typeCast) {
      myFileData.getCasts().add(typeCast);
    }

    @Nullable
    JavacRef.JavacElementRefBase asJavacRef(Element element) {
      return asJavacRef(element, null);
    }

    @Nullable
    JavacRef.JavacElementRefBase asJavacRef(Element element, Element qualifier) {
      return JavacRef.JavacElementRefBase.fromElement(element, qualifier, myNameTableCache);
    }

    @Nullable
    JavacRef.JavacElementRefBase asJavacRef(TypeMirror typeMirror) {
      final Element element = getTypeUtility().asElement(typeMirror);
      return element == null ? null : JavacRef.JavacElementRefBase.fromElement(element, null, myNameTableCache);
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

    JavacNameTable getNameTable() {
      return myNameTableCache;
    }

    long getStartOffset(Tree tree) {
      return myTreeHelper.getStartOffset(tree);
    }

    long getEndOffset(Tree tree) {
      return myTreeHelper.getEndOffset(tree);
    }

    private int decrementRemainDeclarationsAndGet(Tree declarationToProcess) {
      return declarationToProcess == null ? myRemainDeclarations : --myRemainDeclarations;
    }
  }

  private static TObjectIntHashMap<JavacRef> createReferenceHolder() {
    return new TObjectIntHashMap<JavacRef>();
  }

  private static List<JavacDef> createDefinitionHolder() {
    return new ArrayList<JavacDef>();
  }

  private class JavacTreeHelper {
    private final TreePath myUnitPath;
    private final Trees myTreeUtil;
    private final SourcePositions myPositions;

    private JavacTreeHelper(CompilationUnitTree unit, Trees treeUtil) {
      myUnitPath = new TreePath(unit);
      myTreeUtil = treeUtil;
      myPositions = treeUtil.getSourcePositions();
    }

    private long getStartOffset(Tree tree) {
      return myPositions.getStartPosition(myUnitPath.getCompilationUnit(), tree);
    }

    private long getEndOffset(Tree tree) {
      return myPositions.getEndPosition(myUnitPath.getCompilationUnit(), tree);
    }

    private Element getReferencedElement(Tree tree) {
      final TreePath path = new TreePath(myUnitPath, tree);
      if (myAtLeastJdk8) {
        return myTreeUtil.getElement(path);
      } else {
        return getElementIfJdkUnder8(tree);
      }
    }

    private TypeMirror getType(Tree tree) {
      return myTreeUtil.getTypeMirror(new TreePath(myUnitPath, tree));
    }
  }

  private static void incrementOrAdd(TObjectIntHashMap<JavacRef> map, JavacRef key) {
    if (!map.adjustValue(key, 1)) {
      map.put(key, 1);
    }
  }

  //TODO
  private static Element getElementIfJdkUnder8(Tree tree) {
    if (tree == null) return null;
    Field symField;
    try {
      //should be the same to com.sun.tools.javac.tree.TreeInfo.symbolForImpl() since com.sun.source.util.Trees.getElement() works improperly under jdk 6-7
      symField = tree.getClass().getField(tree instanceof NewClassTree ? "constructor" : "sym");
    }
    catch (NoSuchFieldException e) {
      throw new RuntimeException(tree.getClass().getName());
    }
    try {
      return (Element) symField.get(tree);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
