package com.intellij.debugger.engine;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
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
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 2, 2004
 * Time: 8:33:41 PM
 * To change this template use File | Settings | File Templates.
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

  public List<Location> locationsOfLine(ReferenceType type,
                                        SourcePosition position) {
    try {
      int line = position.getLine() + 1;
      List<Location> locs = (getDebugProcess().getVirtualMachineProxy().versionHigher("1.4") ? type.locationsOfLine(DebugProcessImpl.JAVA_STRATUM, null, line) : type.locationsOfLine(line));
      if (locs.size() > 0) {
        return locs;
      }
    }
    catch (AbsentInformationException e) {
    }

    return new ArrayList<Location>();
  }

  public ClassPrepareRequest createPrepareRequest(final ClassPrepareRequestor requestor, final SourcePosition position) {
    PsiClass psiClass = JVMNameUtil.getClassAt(position);
    if(psiClass == null) {
      return null;
    }

    String waitPrepareFor;
    ClassPrepareRequestor waitRequestor;

    if(PsiUtil.isLocalOrAnonymousClass(psiClass)) {
      PsiClass parent = JVMNameUtil.getTopLevelParentClass(psiClass);

      if(parent == null) {
        return null;
      }

      waitPrepareFor = JVMNameUtil.getNonAnonymousClassName(parent) + "$*";
      waitRequestor = new ClassPrepareRequestor() {
        public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
          final CompoundPositionManager positionManager = ((DebugProcessImpl)debuggerProcess).getPositionManager();
          if (positionManager.locationsOfLine(referenceType, position).size() > 0) {
            requestor.processClassPrepare(debuggerProcess, referenceType);
          }
          else {
            final List<ReferenceType> positionClasses = positionManager.getAllClasses(position);
            if (positionClasses.contains(referenceType)) {
              requestor.processClassPrepare(debuggerProcess, referenceType);
            }
          }
        }
      };
    }
    else {
      waitPrepareFor = JVMNameUtil.getNonAnonymousClassName(psiClass);
      waitRequestor = requestor;
    }
    if (waitPrepareFor == null) {
      return null;  // no suitable class found for this name
    }
    return myDebugProcess.getRequestsManager().createClassPrepareRequest(waitRequestor, waitPrepareFor);
  }

  public SourcePosition getSourcePosition(final Location location) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if(location == null) {
      return null;
    }

    PsiFile psiFile = getPsiFileByLocation(getDebugProcess().getProject(), location);
    if(psiFile == null ) {
      return null;
    }

    int     lineNumber  = calcLineIndex(psiFile, location);

    return SourcePosition.createFromLine(psiFile, lineNumber);
  }

  private int calcLineIndex(PsiFile psiFile,
                            Location location) {
    LOG.assertTrue(myDebugProcess != null);
    if (location == null) {
      return -1;
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
        return -1;
      }
      final String methodName = location.method().name();
      if(methodName == null) {
        return -1;
      }
      if(location.declaringType() == null) {
        return -1;
      }

      final MethodFinder finder = new MethodFinder(location.declaringType().name(), methodSignature);
      psiFile.accept(finder);

      final PsiMethod compiledMethod = finder.getCompiledMethod();
      if (compiledMethod == null) {
        return -1;
      }
      final Document document = PsiDocumentManager.getInstance(myDebugProcess.getProject()).getDocument(psiFile);
      if(document == null){
        return -1;
      }
      final int offset = finder.getCompiledMethod().getTextOffset();
      if (offset < 0) {
        return -1;
      }
      return document.getLineNumber(offset);
    }

    return lineNumber;
  }

  private PsiFile getPsiFileByLocation(final Project project, final Location location) {
    if (location == null) {
      return null;
    }
    final ReferenceType refType = location.declaringType();
    if (refType == null) {
      return null;
    }

    final String originalQName = refType.name();
    int dollar = originalQName.indexOf('$');
    final String qName = dollar >= 0 ? originalQName.substring(0, dollar) : originalQName;
    final GlobalSearchScope searchScope = myDebugProcess.getSession().getSearchScope();
    PsiClass psiClass = DebuggerUtils.findClass(qName, project, searchScope);
    if (psiClass == null && dollar >= 0 /*originalName and qName really differ*/) {
      psiClass = DebuggerUtils.findClass(originalQName, project, searchScope); // try to lookup original name
    }
    
    if (psiClass != null) {
      psiClass = (PsiClass)psiClass.getNavigationElement();
      return psiClass.getContainingFile();
    }

    return null;
  }

  public List<ReferenceType> getAllClasses(final SourcePosition classPosition) {
    return ApplicationManager.getApplication().runReadAction(new Computable<List<ReferenceType>> () {
      public List<ReferenceType> compute() {
        final PsiClass psiClass = JVMNameUtil.getClassAt(classPosition);

        if(psiClass == null) {
          return Collections.emptyList();
        }

        if(PsiUtil.isLocalOrAnonymousClass(psiClass)) {
          final PsiClass parentNonLocal = JVMNameUtil.getTopLevelParentClass(psiClass);
          if(parentNonLocal == null) {
            LOG.assertTrue(false, "Local class has no non-local parent");
            return Collections.emptyList();
          }
          final String parentClassName = JVMNameUtil.getNonAnonymousClassName(parentNonLocal);
          final List<ReferenceType> outer = myDebugProcess.getVirtualMachineProxy().classesByName(parentClassName);
          final List<ReferenceType> result = new ArrayList<ReferenceType>();
          findNested(outer, classPosition, result);
          if (result.size() == 0) {
            // no executable code found at this line in any class
            for (ReferenceType refType : outer) {
              final ReferenceType closest = findClosestClassAt(refType, classPosition, psiClass);
              if (closest != null) {
                result.add(closest);
              }
            }
          }
          return result;
        }
        else {
          final String className = JVMNameUtil.getNonAnonymousClassName(psiClass);
          if (className == null) {
            return Collections.emptyList();
          }
          return myDebugProcess.getVirtualMachineProxy().classesByName(className);
        }
      }
    });
  }

  private void findNested(List<ReferenceType> outer, SourcePosition classPosition, final List<ReferenceType> result) {
    if (outer.size() == 0) {
      return;
    }
    for (ReferenceType referenceType : outer) {
      if (referenceType.isPrepared()) {
        final List<ReferenceType> nested = myDebugProcess.getVirtualMachineProxy().nestedTypes(referenceType);
        findNested(nested, classPosition, result);

        try {
          final int lineNumber = classPosition.getLine() + 1;
          if (referenceType.locationsOfLine(lineNumber).size() > 0) {
            result.add(referenceType);
          }
        }
        catch (AbsentInformationException e) {
        }
      }
    }
  }

  @Nullable
  private ReferenceType findClosestClassAt(final ReferenceType from, final SourcePosition classPosition, @NotNull final PsiClass psiClass) {
    if(from.isPrepared()) {
      final List<ReferenceType> nested = myDebugProcess.getVirtualMachineProxy().nestedTypes(from);
      for (ReferenceType nestedType : nested) {
        final ReferenceType foundType = findClosestClassAt(nestedType, classPosition, psiClass);
        if (foundType != null) {
          return foundType;
        }
      }

      try {
        final int lineNumber = classPosition.getLine() + 1;
        final List<Location> locations = from.allLineLocations();
        boolean isGreater = false;
        boolean isLess = false;
        for (Location location : locations) {
          isGreater |= location.lineNumber() <= lineNumber;
          isLess |=  lineNumber <= location.lineNumber();
          if (isGreater && isLess) {
            final SourcePosition position = SourcePosition.createFromLine(psiClass.getContainingFile(), location.lineNumber() - 1);
            return psiClass.equals(JVMNameUtil.getClassAt(position))? from : null;
          }
        }
      }
      catch (AbsentInformationException e) {
      }
    }
    return null;
  }

  private class MethodFinder extends PsiRecursiveElementVisitor {
    private final String myClassName;
    private PsiClass myCompiledClass;
    private final String myMethodSignature;
    private PsiMethod myCompiledMethod;

    public MethodFinder(final String className, final String methodSignature) {
      myClassName = className;
      myMethodSignature = methodSignature;
    }

    public void visitClass(PsiClass aClass) {
      final List<ReferenceType> allClasses = myDebugProcess.getPositionManager().getAllClasses(SourcePosition.createFromElement(aClass));
      for (ReferenceType referenceType : allClasses) {
        if (referenceType.name().equals(myClassName)) {
          myCompiledClass = aClass;
        }
      }

      aClass.acceptChildren(this);
    }

    public void visitMethod(PsiMethod method) {
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
