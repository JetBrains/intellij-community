/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.debugger.NoDataException;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.execution.filters.LineNumbersMapping;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.*;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author lex
 */
public class PositionManagerImpl implements PositionManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.PositionManagerImpl");

  private final DebugProcessImpl myDebugProcess;

  public PositionManagerImpl(DebugProcessImpl debugProcess) {
    myDebugProcess = debugProcess;
  }

  public DebugProcess getDebugProcess() {
    return myDebugProcess;
  }

  @NotNull
  public List<Location> locationsOfLine(@NotNull ReferenceType type, @NotNull SourcePosition position) throws NoDataException {
    try {
      final int line = position.getLine() + 1;
      return type.locationsOfLine(DebugProcess.JAVA_STRATUM, null, line);
    }
    catch (AbsentInformationException ignored) {
    }
    return Collections.emptyList();
  }

  public ClassPrepareRequest createPrepareRequest(@NotNull final ClassPrepareRequestor requestor, @NotNull final SourcePosition position) throws NoDataException {
    final Ref<String> waitPrepareFor = new Ref<String>(null);
    final Ref<ClassPrepareRequestor> waitRequestor = new Ref<ClassPrepareRequestor>(null);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        PsiClass psiClass = JVMNameUtil.getClassAt(position);
        if (psiClass == null) {
          return;
        }

        String className = JVMNameUtil.getNonAnonymousClassName(psiClass);
        if (className == null) {
          final PsiClass parent = JVMNameUtil.getTopLevelParentClass(psiClass);

          if (parent == null) {
            return;
          }

          final String parentQName = JVMNameUtil.getNonAnonymousClassName(parent);
          if (parentQName == null) {
            return;
          }
          waitPrepareFor.set(parentQName + "*");
          waitRequestor.set(new ClassPrepareRequestor() {
            public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
              final CompoundPositionManager positionManager = ((DebugProcessImpl)debuggerProcess).getPositionManager();
              final List<ReferenceType> positionClasses = positionManager.getAllClasses(position);
              if (positionClasses.contains(referenceType)) {
                requestor.processClassPrepare(debuggerProcess, referenceType);
              }
            }
          });
        }
        else {
          waitPrepareFor.set(className);
          waitRequestor.set(requestor);
        }
      }
    });
    if (waitPrepareFor.get() == null) {
      return null;  // no suitable class found for this name
    }
    return myDebugProcess.getRequestsManager().createClassPrepareRequest(waitRequestor.get(), waitPrepareFor.get());
  }

  public SourcePosition getSourcePosition(final Location location) throws NoDataException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if(location == null) {
      return null;
    }

    PsiFile psiFile = getPsiFileByLocation(getDebugProcess().getProject(), location);
    if(psiFile == null ) {
      return null;
    }

    LOG.assertTrue(myDebugProcess != null);
    if (location == null) {
      return SourcePosition.createFromLine(psiFile, -1);
    }

    int lineNumber;
    try {
      lineNumber = location.lineNumber() - 1;
    }
    catch (InternalError e) {
      lineNumber = -1;
    }

    if (lineNumber > -1) {
      SourcePosition position = calcLineMappedSourcePosition(psiFile, lineNumber);
      if (position != null) {
        return position;
      }
    }

    if (psiFile instanceof PsiCompiledElement || lineNumber < 0) {
      final String methodSignature = location.method().signature();
      if (methodSignature == null) {
        return SourcePosition.createFromLine(psiFile, -1);
      }
      final String methodName = location.method().name();
      if(methodName == null) {
        return SourcePosition.createFromLine(psiFile, -1);
      }
      if(location.declaringType() == null) {
        return SourcePosition.createFromLine(psiFile, -1);
      }

      final MethodFinder finder = new MethodFinder(location.declaringType().name(), methodName, methodSignature);
      psiFile.accept(finder);

      final PsiMethod compiledMethod = finder.getCompiledMethod();
      if (compiledMethod == null) {
        return SourcePosition.createFromLine(psiFile, -1);
      }
      SourcePosition sourcePosition = SourcePosition.createFromElement(compiledMethod);
      if (lineNumber >= 0) {
        sourcePosition = new ClsSourcePosition(sourcePosition, lineNumber);
      }
      return sourcePosition;
    }

    return SourcePosition.createFromLine(psiFile, lineNumber);
  }

  @Nullable
  private PsiFile getPsiFileByLocation(final Project project, final Location location) {
    if (location == null) {
      return null;
    }
    final ReferenceType refType = location.declaringType();
    if (refType == null) {
      return null;
    }

    // We should find a class no matter what
    // setAlternativeResolveEnabled is turned on here
    //if (DumbService.getInstance(project).isDumb()) {
    //  return null;
    //}

    final String originalQName = refType.name();
    final GlobalSearchScope searchScope = myDebugProcess.getSearchScope();
    PsiClass psiClass = DebuggerUtils.findClass(originalQName, project, searchScope); // try to lookup original name first
    if (psiClass == null) {
      int dollar = originalQName.indexOf('$');
      if (dollar > 0) {
        final String qName = originalQName.substring(0, dollar);
        psiClass = DebuggerUtils.findClass(qName, project, searchScope);
      }
    }

    if (psiClass != null) {
      PsiElement element = psiClass.getNavigationElement();
      // see IDEA-137167, prefer not compiled elements
      if (element instanceof PsiCompiledElement) {
        PsiElement fileElement = psiClass.getContainingFile().getNavigationElement();
        if (!(fileElement instanceof PsiCompiledElement)) {
          element = fileElement;
        }
      }
      return element.getContainingFile();
    }
    else {
      // try to search by filename
      try {
        PsiFile[] files = FilenameIndex.getFilesByName(project, refType.sourceName(), GlobalSearchScope.allScope(project));
        for (PsiFile file : files) {
          if (file instanceof PsiJavaFile) {
            for (PsiClass cls : ((PsiJavaFile)file).getClasses()) {
              if (StringUtil.equals(originalQName, cls.getQualifiedName())) {
                return file;
              }
            }
          }
        }
      }
      catch (AbsentInformationException ignore) {
      }
    }

    return null;
  }

  @NotNull
  public List<ReferenceType> getAllClasses(@NotNull final SourcePosition position) throws NoDataException {
    final Ref<String> baseClassNameRef = new Ref<String>(null);
    final Ref<PsiClass> classAtPositionRef = new Ref<PsiClass>(null);
    final Ref<Boolean> isLocalOrAnonymous = new Ref<Boolean>(Boolean.FALSE);
    final Ref<Integer> requiredDepth = new Ref<Integer>(0);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final PsiClass psiClass = JVMNameUtil.getClassAt(position);
        if (psiClass != null) {
          classAtPositionRef.set(psiClass);
          String className = JVMNameUtil.getNonAnonymousClassName(psiClass);
          if (className == null) {
            isLocalOrAnonymous.set(Boolean.TRUE);
            final PsiClass topLevelClass = JVMNameUtil.getTopLevelParentClass(psiClass);
            if (topLevelClass != null) {
              final String parentClassName = JVMNameUtil.getNonAnonymousClassName(topLevelClass);
              if (parentClassName != null) {
                requiredDepth.set(getNestingDepth(psiClass));
                baseClassNameRef.set(parentClassName);
              }
            }
            else {
              LOG.error("Local or anonymous class has no non-local parent");
            }
          }
          else {
            baseClassNameRef.set(className);
          }
        }
      }
    });

    final String className = baseClassNameRef.get();
    if (className == null) {
      return Collections.emptyList();
    }

    if (!isLocalOrAnonymous.get()) {
      return myDebugProcess.getVirtualMachineProxy().classesByName(className);
    }
    
    // the name is a parent class for a local or anonymous class
    final List<ReferenceType> outers = myDebugProcess.getVirtualMachineProxy().classesByName(className);
    final List<ReferenceType> result = new ArrayList<ReferenceType>(outers.size());
    for (ReferenceType outer : outers) {
      final ReferenceType nested = findNested(outer, 0, classAtPositionRef.get(), requiredDepth.get(), position);
      if (nested != null) {
        result.add(nested);
      }
    }
    return result;
  }

  private static int getNestingDepth(PsiClass aClass) {
    int depth = 0;
    PsiClass enclosing = getEnclosingClass(aClass);
    while (enclosing != null) {
      depth++;
      enclosing = getEnclosingClass(enclosing);
    }
    return depth;
  }

  /**
   * See IDEA-121739
   * Anonymous classes inside other anonymous class parameters list should belong to parent class
   * Inner in = new Inner(new Inner2(){}) {};
   * Parent of Inner2 sub class here is not Inner sub class
   */
  private static PsiClass getEnclosingClass(PsiElement element) {
    if (element == null) {
      return null;
    }

    element = element.getParent();
    PsiElement previous = null;

    while (element != null) {
      if (PsiClass.class.isInstance(element) && !(previous instanceof PsiExpressionList)) {
        //noinspection unchecked
        return (PsiClass)element;
      }
      if (element instanceof PsiFile) {
        return null;
      }
      previous = element;
      element = element.getParent();
    }

    return null;
  }

  @Nullable
  private ReferenceType findNested(final ReferenceType fromClass, final int currentDepth, final PsiClass classToFind, final int requiredDepth, final SourcePosition position) {
    final VirtualMachineProxyImpl vmProxy = myDebugProcess.getVirtualMachineProxy();
    if (fromClass.isPrepared()) {
      try {
        if (currentDepth < requiredDepth) {
          final List<ReferenceType> nestedTypes = vmProxy.nestedTypes(fromClass);
          for (ReferenceType nested : nestedTypes) {
            final ReferenceType found = findNested(nested, currentDepth + 1, classToFind, requiredDepth, position);
            if (found != null) {
              return found;
            }
          }
          return null;
        }

        int rangeBegin = Integer.MAX_VALUE;
        int rangeEnd = Integer.MIN_VALUE;
        for (Location location : fromClass.allLineLocations()) {
          final int lnumber = location.lineNumber();
          if (lnumber <= 1) {
            // should be a native method, skipping
            // sometimes compiler generates location where line number is exactly 1 (e.g. GWT)
            // such locations are hardly correspond to real lines in code, so skipping them too
            continue;
          }
          final Method method = location.method();
          if (method == null || DebuggerUtils.isSynthetic(method) || method.isBridge()) {
            // do not take into account synthetic stuff
            continue;
          }
          int locationLine = lnumber - 1;
          PsiFile psiFile = position.getFile().getOriginalFile();
          if (psiFile instanceof PsiCompiledFile) {
            locationLine = bytecodeToSourceLine(psiFile, locationLine);
            if (locationLine < 0) continue;
          }
          rangeBegin = Math.min(rangeBegin,  locationLine);
          rangeEnd = Math.max(rangeEnd,  locationLine);
        }

        final int positionLine = position.getLine();
        if (positionLine >= rangeBegin && positionLine <= rangeEnd) {
          // choose the second line to make sure that only this class' code exists on the line chosen
          // Otherwise the line (depending on the offset in it) can contain code that belongs to different classes
          // and JVMNameUtil.getClassAt(candidatePosition) will return the wrong class.
          // Example of such line:
          // list.add(new Runnable(){......
          // First offsets belong to parent class, and offsets inside te substring "new Runnable(){" belong to anonymous runnable.
          final int finalRangeBegin = rangeBegin;
          final int finalRangeEnd = rangeEnd;
          return ApplicationManager.getApplication().runReadAction(new NullableComputable<ReferenceType>() {
            public ReferenceType compute() {
              if (!classToFind.isValid()) {
                return null;
              }
              final int line = Math.min(finalRangeBegin + 1, finalRangeEnd);
              final SourcePosition candidatePosition = positionLine == line? position : SourcePosition.createFromLine(position.getFile(), line);
              return classToFind.equals(JVMNameUtil.getClassAt(candidatePosition)) ? fromClass : null;
            }
          });
        }
      }
      catch (AbsentInformationException ignored) {
      }
    }
    return null;
  }

  //don't use JavaRecursiveElementWalkingVisitor because getNextSibling() works slowly for compiled elements 
  private class MethodFinder extends JavaRecursiveElementVisitor {
    private final String myClassName;
    private PsiClass myCompiledClass;
    private final String myMethodName;
    private final String myMethodSignature;
    private PsiMethod myCompiledMethod;

    public MethodFinder(final String className, final String methodName, final String methodSignature) {
      myClassName = className;
      myMethodName = methodName;
      myMethodSignature = methodSignature;
    }

    @Override public void visitClass(PsiClass aClass) {
      final List<ReferenceType> allClasses = myDebugProcess.getPositionManager().getAllClasses(SourcePosition.createFromElement(aClass));
      for (ReferenceType referenceType : allClasses) {
        if (referenceType.name().equals(myClassName)) {
          myCompiledClass = aClass;
        }
      }

      aClass.acceptChildren(this);
    }

    @Override public void visitMethod(PsiMethod method) {
      try {
        //noinspection HardCodedStringLiteral
        String methodName = method.isConstructor() ? "<init>" : method.getName();
        PsiClass containingClass = method.getContainingClass();

        if(containingClass != null &&
           containingClass.equals(myCompiledClass) &&
           methodName.equals(myMethodName) &&
           JVMNameUtil.getJVMSignature(method).getName(myDebugProcess).equals(myMethodSignature)) {
          myCompiledMethod = method;
        }
      }
      catch (EvaluateException e) {
        LOG.debug(e);
      }
    }

    public PsiClass getCompiledClass() {
      return myCompiledClass;
    }

    public PsiMethod getCompiledMethod() {
      return myCompiledMethod;
    }
  }

  private static class ClsSourcePosition extends SourcePosition {
    private SourcePosition myDelegate;
    private int myOriginalLine;

    public ClsSourcePosition(SourcePosition delegate, int originalLine) {
      myDelegate = delegate;
      myOriginalLine = originalLine;
    }

    @Override
    @NotNull
    public PsiFile getFile() {
      return myDelegate.getFile();
    }

    @Override
    public PsiElement getElementAt() {
      return myDelegate.getElementAt();
    }

    @Override
    public int getLine() {
      int line = myDelegate.getLine();
      if (myOriginalLine >= 0) {
        return mapDelegate().getLine();
      }
      return line;
    }

    @Override
    public int getOffset() {
      int offset = myDelegate.getOffset(); //document loaded here
      if (myOriginalLine >= 0) {
        return mapDelegate().getOffset();
      }
      return offset;
    }

    private SourcePosition mapDelegate() {
      SourcePosition position = calcLineMappedSourcePosition(myDelegate.getFile(), myOriginalLine);
      if (position != null) {
        myDelegate = position;
      }
      myOriginalLine = -1;
      return myDelegate;
    }

    @Override
    public Editor openEditor(boolean requestFocus) {
      return myDelegate.openEditor(requestFocus);
    }

    @Override
    public boolean equals(Object o) {
      return myDelegate.equals(o);
    }

    @Override
    public void navigate(boolean requestFocus) {
      myDelegate.navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
      return myDelegate.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
      return myDelegate.canNavigateToSource();
    }
  }

  @Nullable
  private static SourcePosition calcLineMappedSourcePosition(PsiFile psiFile, int originalLine) {
    int line = bytecodeToSourceLine(psiFile, originalLine);
    if (line > -1) {
      return SourcePosition.createFromLine(psiFile, line - 1);
    }
    return null;
  }

  private static int bytecodeToSourceLine(PsiFile psiFile, int originalLine) {
    VirtualFile file = psiFile.getVirtualFile();
    if (file != null) {
      LineNumbersMapping mapping = file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY);
      if (mapping != null) {
        int line = mapping.bytecodeToSource(originalLine + 1);
        if (line > -1) {
          return line;
        }
      }
    }
    return -1;
  }
}
