// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac.ast;

import com.intellij.util.Consumer;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import gnu.trove.THashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.javac.ast.api.*;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaCompiler;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

final class JavacReferenceCollectorListener implements TaskListener {
  private final Consumer<? super JavacFileData> myDataConsumer;
  private final JavacTask myJavacTask;
  private final JavacTreeRefScanner myAstScanner;
  private final boolean myAtLeastJdk8;

  private boolean myInitialized;
  private Elements myElementUtility;
  private Types myTypeUtility;
  private Trees myTreeUtility;
  private JavacNameTable myNameTableCache;

  private final Map<String, ReferenceCollector> myIncompletelyProcessedFiles = new HashMap<String, ReferenceCollector>(10);

  static void installOn(JavaCompiler.CompilationTask task, Consumer<? super JavacFileData> dataConsumer) {
    JavacTask javacTask = (JavacTask)task;
    Method addTaskMethod; // jdk >= 8
    try {
      addTaskMethod = JavacTask.class.getMethod("addTaskListener", TaskListener.class);
    }
    catch (NoSuchMethodException e) {
      addTaskMethod = null;
    }
    final JavacReferenceCollectorListener taskListener = new JavacReferenceCollectorListener(
      dataConsumer, javacTask, addTaskMethod != null
    );
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

  private JavacReferenceCollectorListener(Consumer<? super JavacFileData> dataConsumer,
                                          JavacTask javacTask,
                                          boolean atLeastJdk8) {
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
          final JavacRef.ImportProperties importProps = JavacRef.ImportProperties.create(anImport.isStatic(), myNameTableCache.isAsterisk(name));
          if (!importProps.isOnDemand()) {
            // member import
            for (Element memberElement : myElementUtility.getAllMembers((TypeElement)ownerElement)) {
              if (memberElement.getSimpleName() == name) {
                incrementOrAdd(elements, JavacRef.JavacElementRefBase.fromElement(null, memberElement, null, myNameTableCache, importProps));
              }
            }
          }
          collectClassImports(ownerElement, elements, importProps);
        }
      } else {
        // class import
        collectClassImports(element, elements, JavacRef.ImportProperties.create(anImport.isStatic(), false));
      }
    }
  }

  private void collectClassImports(Element baseImport, TObjectIntHashMap<JavacRef> collector, final JavacRef.ImportProperties importProps) {
    for (Element element = baseImport;
         element != null && element.getKind() != ElementKind.PACKAGE;
         element = element.getEnclosingElement()) {
      incrementOrAdd(collector, JavacRef.JavacElementRefBase.fromElement(null, element, null, myNameTableCache, importProps));
    }
  }

  class ReferenceCollector {
    private final JavacFileData myFileData;
    private final JavacTreeHelper myTreeHelper;
    private int myRemainDeclarations;
    private final JavacRef.JavacClass myPackageInfo;
    
    private ReferenceCollector(int remainDeclarations,
                               String filePath,
                               CompilationUnitTree unitTree) {
      myRemainDeclarations = remainDeclarations;
      myFileData = new JavacFileData(
        filePath, createReferenceHolder(), new ArrayList<JavacTypeCast>(), createDefinitionHolder(), new THashSet<JavacRef>()
      );
      myTreeHelper = new JavacTreeHelper(unitTree, myTreeUtility);

      if (isPackageInfo(filePath)) {
        final ExpressionTree packageName = unitTree.getPackageName();
        final String pack = packageName != null ? packageName.toString() : "";
        myPackageInfo = new JavacRef.JavacClassImpl(false, Collections.<Modifier>emptySet(), pack.isEmpty()? "package-info" : pack + ".package-info");
        sinkDeclaration(new JavacDef.JavacClassDef(myPackageInfo, JavacRef.EMPTY_ARRAY));
      }
      else {
        myPackageInfo = null;
      }
    }

    private boolean isPackageInfo(String filePath) {
      if (filePath != null && filePath.endsWith("package-info.java")) {
        final int idx = filePath.length() - "package-info.java".length() - 1;
        return idx >= 0 && (filePath.charAt(idx) == '/' || filePath.charAt(idx) == File.separatorChar);
      }
      return false;
    }

    void sinkReference(@Nullable JavacRef.JavacElementRefBase ref) {
      incrementOrAdd(myFileData.getRefs(), ref);
    }

    void sinkDeclaration(JavacDef def) {
     myFileData.getDefs().add(def);
    }

    void sinkImplicitToString(@Nullable JavacRef ref) {
      if (ref != null) {
        myFileData.getImplicitToStringRefs().add(ref);
      }
    }

    public void sinkTypeCast(JavacTypeCast typeCast) {
      myFileData.getCasts().add(typeCast);
    }

    @Nullable
    JavacRef.JavacElementRefBase asJavacRef(final Element containingClass, Element element) {
      return asJavacRef(containingClass, element, null);
    }

    @Nullable
    JavacRef.JavacElementRefBase asJavacRef(final Element containingClass, Element element, Element qualifier) {
      return JavacRef.JavacElementRefBase.fromElement(getContainingClassName(containingClass), element, qualifier, myNameTableCache);
    }

    @Nullable
    JavacRef.JavacElementRefBase asJavacRef(final Element containingClass, TypeMirror typeMirror) {
      final Element element = getTypeUtility().asElement(typeMirror);
      return element == null ? null : JavacRef.JavacElementRefBase.fromElement(getContainingClassName(containingClass), element, null, myNameTableCache);
    }

    private String getContainingClassName(Element containingClass) {
      return containingClass != null? myNameTableCache.parseBinaryName(containingClass) : myPackageInfo != null? myPackageInfo.getName() : null;
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
    if (tree == null || tree instanceof PrimitiveTypeTree || tree instanceof ArrayTypeTree) return null;
    if (tree instanceof ParameterizedTypeTree) {
      return getElementIfJdkUnder8(((ParameterizedTypeTree)tree).getType());
    }
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
