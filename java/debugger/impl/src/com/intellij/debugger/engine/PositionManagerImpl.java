/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
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
  public List<Location> locationsOfLine(ReferenceType type, SourcePosition position) throws NoDataException {
    try {
      int line = position.getLine() + 1;
      List<Location> locs = (getDebugProcess().getVirtualMachineProxy().versionHigher("1.4")
                             ? type.locationsOfLine(DebugProcessImpl.JAVA_STRATUM, null, line) : type.locationsOfLine(line));
      if (locs.size() > 0) {
        return locs;
      }
    }
    catch (AbsentInformationException ignored) {
    }

    return Collections.emptyList();
  }

  public ClassPrepareRequest createPrepareRequest(final ClassPrepareRequestor requestor, final SourcePosition position) throws NoDataException {
    final Ref<String> waitPrepareFor = new Ref<String>(null);
    final Ref<ClassPrepareRequestor> waitRequestor = new Ref<ClassPrepareRequestor>(null);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        PsiClass psiClass = JVMNameUtil.getClassAt(position);
        if (psiClass == null) {
          return;
        }

        if (PsiUtil.isLocalOrAnonymousClass(psiClass)) {
          PsiClass parent = JVMNameUtil.getTopLevelParentClass(psiClass);

          if (parent == null) {
            return;
          }

          final String parentQName = JVMNameUtil.getNonAnonymousClassName(parent);
          if (parentQName == null) {
            return;
          }
          waitPrepareFor.set(parentQName + "$*");
          waitRequestor.set(new ClassPrepareRequestor() {
            public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
              final CompoundPositionManager positionManager = ((DebugProcessImpl)debuggerProcess).getPositionManager();
              final List<ReferenceType> positionClasses = positionManager.getAllClasses(position);
              if (positionClasses.isEmpty()) {
                // fallback
                if (positionManager.locationsOfLine(referenceType, position).size() > 0) {
                  requestor.processClassPrepare(debuggerProcess, referenceType);
                }
              }
              else {
                if (positionClasses.contains(referenceType)) {
                  requestor.processClassPrepare(debuggerProcess, referenceType);
                }
              }
            }
          });
        }
        else {
          waitPrepareFor.set(JVMNameUtil.getNonAnonymousClassName(psiClass));
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

      final MethodFinder finder = new MethodFinder(location.declaringType().name(), methodSignature);
      psiFile.accept(finder);

      final PsiMethod compiledMethod = finder.getCompiledMethod();
      if (compiledMethod == null) {
        return SourcePosition.createFromLine(psiFile, -1);
      }
      return SourcePosition.createFromElement(compiledMethod);
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

    if (DumbService.getInstance(project).isDumb()) {
      return null;
    }

    final String originalQName = refType.name();
    int dollar = originalQName.indexOf('$');
    final String qName = dollar >= 0 ? originalQName.substring(0, dollar) : originalQName;
    final GlobalSearchScope searchScope = myDebugProcess.getSearchScope();
    PsiClass psiClass = DebuggerUtils.findClass(qName, project, searchScope);
    if (psiClass == null && dollar >= 0 /*originalName and qName really differ*/) {
      psiClass = DebuggerUtils.findClass(originalQName, project, searchScope); // try to lookup original name
    }
    
    if (psiClass != null) {
      final PsiElement element = psiClass.getNavigationElement();
      return element.getContainingFile();
    }

    return null;
  }

  @NotNull
  public List<ReferenceType> getAllClasses(final SourcePosition classPosition) throws NoDataException {
    final Trinity<String, Boolean, PsiClass> trinity = calcClassName(classPosition);
    if (trinity == null) {
      return Collections.emptyList();
    }
    final String className = trinity.getFirst();
    final boolean isNonAnonymousClass = trinity.getSecond();
    final PsiClass classAtPosition = trinity.getThird();

    if (isNonAnonymousClass) {
      return myDebugProcess.getVirtualMachineProxy().classesByName(className);
    }
    
    // the name is a parent class for a local or anonymous class
    final List<ReferenceType> outers = myDebugProcess.getVirtualMachineProxy().classesByName(className);
    final List<ReferenceType> result = new ArrayList<ReferenceType>(outers.size());
    for (ReferenceType outer : outers) {
      final ReferenceType nested = findNested(outer, classAtPosition, classPosition);
      if (nested != null) {
        result.add(nested);
      }
    }
    return result;
  }

  @Nullable
  private static Trinity<String, Boolean, PsiClass> calcClassName(final SourcePosition classPosition) {
    return ApplicationManager.getApplication().runReadAction(new NullableComputable<Trinity<String, Boolean, PsiClass>>() {
      public Trinity<String, Boolean, PsiClass> compute() {
        final PsiClass psiClass = JVMNameUtil.getClassAt(classPosition);

        if(psiClass == null) {
          return null;
        }

        if(PsiUtil.isLocalOrAnonymousClass(psiClass)) {
          final PsiClass parentNonLocal = JVMNameUtil.getTopLevelParentClass(psiClass);
          if(parentNonLocal == null) {
            LOG.error("Local or anonymous class has no non-local parent");
            return null;
          }
          final String parentClassName = JVMNameUtil.getNonAnonymousClassName(parentNonLocal);
          if(parentClassName == null) {
            LOG.error("The name of a parent of a local (anonymous) class is null");
            return null;
          }
          return new Trinity<String, Boolean, PsiClass>(parentClassName, Boolean.FALSE, psiClass);
        }
        
        final String className = JVMNameUtil.getNonAnonymousClassName(psiClass);
        return className != null? new Trinity<String, Boolean, PsiClass>(className, Boolean.TRUE, psiClass) : null;
      }
    });
  }

  @Nullable
  private ReferenceType findNested(final ReferenceType fromClass, final PsiClass classToFind, SourcePosition classPosition) {
    final VirtualMachineProxyImpl vmProxy = myDebugProcess.getVirtualMachineProxy();
    if (fromClass.isPrepared()) {
      
      final List<ReferenceType> nestedTypes = vmProxy.nestedTypes(fromClass);
      
      try {
        final int lineNumber = classPosition.getLine() + 1;

        for (ReferenceType nested : nestedTypes) {
          final ReferenceType found = findNested(nested, classToFind, classPosition);
          if (found != null) {
            // check if enclosing class also has executable code at the same line, and if yes, prefer enclosing class 
            return fromClass.locationsOfLine(lineNumber).isEmpty()? found : fromClass;
          }
        }

        if (fromClass.locationsOfLine(lineNumber).size() > 0) {
          return fromClass;
        }
        
        int rangeBegin = Integer.MAX_VALUE;
        int rangeEnd = Integer.MIN_VALUE;
        for (Location location : fromClass.allLineLocations()) {
          final int locationLine = location.lineNumber() - 1;
          rangeBegin = Math.min(rangeBegin,  locationLine);
          rangeEnd = Math.max(rangeEnd,  locationLine);
        }

        if (classPosition.getLine() >= rangeBegin && classPosition.getLine() <= rangeEnd) {
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
              final int line = Math.min(finalRangeBegin + 1, finalRangeEnd);
              final SourcePosition candidatePosition = SourcePosition.createFromLine(classToFind.getContainingFile(), line);
              return classToFind.equals(JVMNameUtil.getClassAt(candidatePosition))? fromClass : null;
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
    private final String myMethodSignature;
    private PsiMethod myCompiledMethod;

    public MethodFinder(final String className, final String methodSignature) {
      myClassName = className;
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
           methodName.equals(methodName) &&
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
}
