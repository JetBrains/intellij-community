// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.MultiRequestPositionManager;
import com.intellij.debugger.NoDataException;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.AlternativeJreClassFinder;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.util.*;
import java.util.function.Consumer;

public class PositionManagerImpl implements PositionManager, MultiRequestPositionManager {
  private static final Logger LOG = Logger.getInstance(PositionManagerImpl.class);

  private final DebugProcessImpl myDebugProcess;

  public PositionManagerImpl(DebugProcessImpl debugProcess) {
    myDebugProcess = debugProcess;
  }

  public DebugProcess getDebugProcess() {
    return myDebugProcess;
  }

  @Override
  public @NotNull List<Location> locationsOfLine(@NotNull ReferenceType type, @NotNull SourcePosition position) throws NoDataException {
    try {
      return DebuggerUtilsAsync.locationsOfLineSync(type, DebugProcess.JAVA_STRATUM, null, position.getLine() + 1);
    }
    catch (AbsentInformationException ignored) {
    }
    return Collections.emptyList();
  }

  @Override
  public ClassPrepareRequest createPrepareRequest(final @NotNull ClassPrepareRequestor requestor, final @NotNull SourcePosition position)
    throws NoDataException {
    throw new IllegalStateException("This class implements MultiRequestPositionManager, corresponding createPrepareRequests version should be used");
  }

  @Override
  public @NotNull List<ClassPrepareRequest> createPrepareRequests(final @NotNull ClassPrepareRequestor requestor, final @NotNull SourcePosition position)
    throws NoDataException {
    return ReadAction.compute(() -> {
      List<ClassPrepareRequest> res = new ArrayList<>();
      for (PsiClass psiClass : getLineClasses(position.getFile(), position.getLine())) {
        ClassPrepareRequestor prepareRequestor = requestor;
        String classPattern = JVMNameUtil.getNonAnonymousClassName(psiClass);
        if (classPattern == null) {
          final PsiClass parent = JVMNameUtil.getTopLevelParentClass(psiClass);
          if (parent == null) {
            continue;
          }
          final String parentQName = JVMNameUtil.getNonAnonymousClassName(parent);
          if (parentQName == null) {
            continue;
          }
          classPattern = parentQName + "*";
          prepareRequestor = new ClassPrepareRequestor() {
            @Override
            public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
              if (((DebugProcessImpl)debuggerProcess).getPositionManager().getAllClasses(position).contains(referenceType)) {
                requestor.processClassPrepare(debuggerProcess, referenceType);
              }
            }
          };
        }
        ClassPrepareRequest request = myDebugProcess.getRequestsManager().createClassPrepareRequest(prepareRequestor, classPattern);
        if (request != null) {
          res.add(request);
        }
      }
      return res;
    });
  }

  @Override
  public @Nullable SourcePosition getSourcePosition(final Location location) throws NoDataException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (location == null) {
      return null;
    }

    Project project = getDebugProcess().getProject();
    PsiFile psiFile = getPsiFileByLocation(project, location);
    if (psiFile == null) {
      return null;
    }

    LOG.assertTrue(myDebugProcess != null);

    int lineNumber = DebuggerUtilsEx.getLineNumber(location, true);

    String qName = location.declaringType().name();

    // replace file with alternative
    String altFileUrl = DebuggerUtilsEx.getAlternativeSourceUrl(qName, project);
    if (altFileUrl != null) {
      VirtualFile altFile = VirtualFileManager.getInstance().findFileByUrl(altFileUrl);
      if (altFile != null) {
        PsiFile altPsiFile = psiFile.getManager().findFile(altFile);
        if (altPsiFile != null) {
          psiFile = altPsiFile;
        }
      }
    }

    SourcePosition sourcePosition = null;
    if (lineNumber > -1) {
      sourcePosition = calcLineMappedSourcePosition(psiFile, lineNumber);
    }

    final Method method = DebuggerUtilsEx.getMethod(location);

    if (sourcePosition == null && (psiFile instanceof PsiCompiledElement || lineNumber < 0)) {
      if (method != null && method.name() != null && method.signature() != null) {
        PsiClass psiClass = findPsiClassByName(qName, null);
        PsiMethod compiledMethod = findMethod(psiClass != null ? psiClass : psiFile, qName, method.name(), method.signature());
        if (compiledMethod != null) {
          sourcePosition = SourcePosition.createFromElement(compiledMethod);
          if (lineNumber >= 0) {
            sourcePosition = new ClsSourcePosition(sourcePosition, lineNumber);
          }
        }
      }
      else {
        return SourcePosition.createFromLine(psiFile, -1);
      }
    }

    if (sourcePosition == null) {
      sourcePosition = SourcePosition.createFromLine(psiFile, lineNumber);
    }

    int lambdaOrdinal = -1;
    if (DebuggerUtilsEx.isLambda(method)) {
      int line = sourcePosition.getLine() + 1;
      Set<Method> lambdas = StreamEx.of(location.declaringType().methods())
        .filter(DebuggerUtilsEx::isLambda)
        .filter(m -> !DebuggerUtilsEx.locationsOfLine(m, line).isEmpty())
        .toSet();
      if (lambdas.size() > 1) {
        ArrayList<Method> lambdasList = new ArrayList<>(lambdas);
        lambdasList.sort(DebuggerUtilsEx.LAMBDA_ORDINAL_COMPARATOR);
        lambdaOrdinal = lambdasList.indexOf(method);
      }
    }

    SourcePosition condRetPos = adjustPositionForConditionalReturn(myDebugProcess, location, psiFile, lineNumber);
    if (condRetPos != null) {
      sourcePosition = condRetPos;
    }

    return new JavaSourcePosition(sourcePosition, location.declaringType(), method, lambdaOrdinal);
  }

  public static @Nullable SourcePosition adjustPositionForConditionalReturn(DebugProcess debugProcess, Location location, PsiFile file, int lineNumber) {
    if (location.virtualMachine().canGetBytecodes()) {
      PsiElement ret = JavaLineBreakpointType.findSingleConditionalReturn(file, lineNumber);
      if (ret != null) {
        byte[] bytecodes = DebuggerUtilsEx.getMethod(location).bytecodes();
        int bytecodeOffs = Math.toIntExact(location.codeIndex());
        // Implicit return instruction at the end of bytecode should not be treated as conditional return.
        // (Note that we also relay on the fact that all return instructions have no operands.)
        if (0 <= bytecodeOffs && bytecodeOffs < bytecodes.length - 1) {
          int opcode = bytecodes[bytecodeOffs] & 0xFF;
          if (Opcodes.IRETURN <= opcode && opcode <= Opcodes.RETURN) {
            return ReadAction.compute(() -> SourcePosition.createFromOffset(file, ret.getTextOffset()));
          }
        }
      }
    }
    return null;
  }

  public static class JavaSourcePosition extends RemappedSourcePosition {
    private final String myExpectedClassName;
    private final String myExpectedMethodName;
    private final int myLambdaOrdinal;

    public JavaSourcePosition(@NotNull SourcePosition delegate, ReferenceType declaringType, Method method, int lambdaOrdinal) {
      super(delegate);
      myExpectedClassName = declaringType != null ? declaringType.name() : null;
      myExpectedMethodName = method != null ? method.name() : null;
      myLambdaOrdinal = lambdaOrdinal;
    }

    public JavaSourcePosition(@NotNull SourcePosition delegate, int lambdaOrdinal) {
      super(delegate);
      assert lambdaOrdinal > -1;
      myExpectedClassName = null;
      myExpectedMethodName = "lambda$"; // fake lambda name
      myLambdaOrdinal = lambdaOrdinal;
    }

    private PsiElement remapElement(PsiElement element) {
      String name = JVMNameUtil.getClassVMName(PsiUtil.getContainingClass(element));
      if (name != null && !name.equals(myExpectedClassName)) {
        return null;
      }
      PsiElement method = DebuggerUtilsEx.getContainingMethod(element);
      if (!StringUtil.isEmpty(myExpectedMethodName)) {
        if (method == null) {
          return null;
        }
        else if (((method instanceof PsiMethod && myExpectedMethodName.equals(((PsiMethod)method).getName())) ||
                  (method instanceof PsiLambdaExpression && DebuggerUtilsEx.isLambdaName(myExpectedMethodName))) &&
                 insideBody(element, DebuggerUtilsEx.getBody(method))) {
          return element;
        }
      }
      return null;
    }

    private static boolean insideBody(@NotNull PsiElement element, @Nullable PsiElement body) {
      if (!PsiTreeUtil.isAncestor(body, element, false)) return false;
      if (body instanceof PsiCodeBlock) {
        return !element.equals(((PsiCodeBlock)body).getRBrace()) && !element.equals(((PsiCodeBlock)body).getLBrace());
      }
      return true;
    }

    @Override
    public SourcePosition mapDelegate(final SourcePosition original) {
      return ReadAction.compute(() -> {
        PsiFile file = original.getFile();
        int line = original.getLine();

        Document document = file.getViewProvider().getDocument();
        if (document == null || line >= document.getLineCount()) {
          return original;
        }

        if (DebuggerUtilsEx.isLambdaName(myExpectedMethodName) && myLambdaOrdinal > -1) {
          List<PsiLambdaExpression> lambdas = DebuggerUtilsEx.collectLambdas(original, true);

          if (myLambdaOrdinal < lambdas.size()) {
            PsiElement firstElem = DebuggerUtilsEx.getFirstElementOnTheLine(lambdas.get(myLambdaOrdinal), document, line);
            if (firstElem != null) {
              return SourcePosition.createFromElement(firstElem);
            }
          }
        }
        else {
          // There may be more than one class/method code on the line, so we need to find out the correct place
          Ref<SourcePosition> res = Ref.create();
          XDebuggerUtil.getInstance().iterateLine(file.getProject(), document, line, elem -> {
            PsiElement remappedElement = remapElement(elem);
            if (remappedElement != null) {
              if (remappedElement.getTextOffset() > original.getOffset()) {
                res.set(SourcePosition.createFromElement(remappedElement));
              }
              return false;
            }
            return true;
          });
          if (!res.isNull()) {
            return res.get();
          }
        }
        return original;
      });
    }
  }

  @RequiresReadLock
  protected static Set<PsiClass> getLineClasses(final PsiFile file, int lineNumber) {
    Document document = file.getViewProvider().getDocument();
    Set<PsiClass> res = new HashSet<>();
    if (document != null) {
      XDebuggerUtil.getInstance().iterateLine(file.getProject(), document, lineNumber, element -> {
        PsiClass aClass = PsiUtil.getContainingClass(element);
        if (aClass != null) {
          res.add(aClass);
        }
        return true;
      });
    }
    return res;
  }

  protected @Nullable PsiFile getPsiFileByLocation(final Project project, final Location location) {
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

    Ref<PsiFile> altSource = new Ref<>();
    PsiClass psiClass = findPsiClassByName(originalQName, c -> altSource.set(findAlternativeJreSourceFile(c)));

    if (!altSource.isNull()) {
      return altSource.get();
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
            for (PsiClass cls : PsiTreeUtil.findChildrenOfAnyType(file, PsiClass.class)) {
              if (StringUtil.equals(originalQName, JVMNameUtil.getClassVMName(cls))) {
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

  private PsiClass findPsiClassByName(String originalQName, @Nullable Consumer<? super ClsClassImpl> altClsProcessor) {
    PsiClass psiClass = null;
    // first check alternative jre if any
    Sdk alternativeJre = myDebugProcess.getSession().getAlternativeJre();
    if (alternativeJre != null) {
      GlobalSearchScope scope = AlternativeJreClassFinder.getSearchScope(alternativeJre);
      psiClass = findClass(myDebugProcess.getProject(), originalQName, scope, false);
      if (psiClass instanceof ClsClassImpl && altClsProcessor != null) { //try to find sources
        altClsProcessor.accept((ClsClassImpl)psiClass);
      }
    }

    if (psiClass == null) {
      psiClass = findClass(myDebugProcess.getProject(), originalQName, myDebugProcess.getSearchScope(), true);
    }
    return psiClass;
  }

  public static @Nullable PsiClass findClass(Project project, String originalQName, GlobalSearchScope searchScope, boolean fallbackToAllScope) {
    PsiClass psiClass = DebuggerUtils.findClass(originalQName, project, searchScope, fallbackToAllScope); // try to lookup original name first
    if (psiClass == null) {
      int dollar = originalQName.indexOf('$');
      if (dollar > 0) {
        psiClass = DebuggerUtils.findClass(originalQName.substring(0, dollar), project, searchScope, fallbackToAllScope);
      }
    }
    return psiClass;
  }

  private @Nullable PsiFile findAlternativeJreSourceFile(ClsClassImpl psiClass) {
    String sourceFileName = psiClass.getSourceFileName();
    String packageName = ((PsiClassOwner)psiClass.getContainingFile()).getPackageName();
    String relativePath = packageName.isEmpty() ? sourceFileName : packageName.replace('.', '/') + '/' + sourceFileName;
    Sdk alternativeJre = myDebugProcess.getSession().getAlternativeJre();

    if (alternativeJre != null) {
      for (VirtualFile file : AlternativeJreClassFinder.getSourceRoots(alternativeJre)) {
        VirtualFile source = file.findFileByRelativePath(relativePath);
        if (source != null && source.isValid()) {
          PsiFile psiSource = psiClass.getManager().findFile(source);
          if (psiSource instanceof PsiClassOwner) {
            return psiSource;
          }
        }
      }
    }
    return null;
  }

  @Override
  public @NotNull @Unmodifiable List<ReferenceType> getAllClasses(final @NotNull SourcePosition position) throws NoDataException {
    Set<PsiClass> lineClasses = ReadAction.compute(() -> getLineClasses(position.getFile(), position.getLine()));
    return ContainerUtil.flatMap(lineClasses, aClass -> getClassReferences(aClass, position));
  }

  private List<ReferenceType> getClassReferences(@NotNull PsiClass psiClass, SourcePosition position) {
    record ClassInfo(boolean isLocalOrAnonymous, int requiredDepth, String className) {
      @RequiresReadLock
      static ClassInfo create(@NotNull PsiClass psiClass) {
        boolean isLocalOrAnonymous = false;
        int requiredDepth = 0;
        String className = JVMNameUtil.getNonAnonymousClassName(psiClass);
        if (className == null) {
          isLocalOrAnonymous = true;
          Pair<PsiClass, Integer> enclosing = getTopOrStaticEnclosingClass(psiClass);
          PsiClass topLevelClass = enclosing.first;
          if (topLevelClass != null) {
            final String parentClassName = JVMNameUtil.getNonAnonymousClassName(topLevelClass);
            if (parentClassName != null) {
              requiredDepth = enclosing.second;
              className = parentClassName;
            }
          }
          else {
            final StringBuilder sb = new StringBuilder();
            PsiTreeUtil.treeWalkUp(psiClass, null, (element, element2) -> {
              sb.append('\n').append(element);
              return true;
            });
            LOG.info("Local or anonymous class " + psiClass + " has no non-local parent, parents:" + sb);
          }
        }
        return new ClassInfo(isLocalOrAnonymous, requiredDepth, className);
      }
    }
    ClassInfo classInfo = ReadAction.compute(() -> ClassInfo.create(psiClass));

    if (classInfo.className == null) {
      return Collections.emptyList();
    }

    List<ReferenceType> matchingClasses = myDebugProcess.getVirtualMachineProxy().classesByName(classInfo.className);
    if (!classInfo.isLocalOrAnonymous) {
      return matchingClasses;
    }

    if (matchingClasses.isEmpty()) { // sometimes inner classes may be loaded before outer
      return StreamEx.of(myDebugProcess.getVirtualMachineProxy().allClasses())
        .filter(t -> t.name().startsWith(classInfo.className))
        .map(outer -> findNested(outer, 0, psiClass, 0, position))
        .nonNull()
        .toList();
    }

    return StreamEx.of(matchingClasses)
      .map(outer -> findNested(outer, 0, psiClass, classInfo.requiredDepth, position))
      .nonNull()
      .toList();
  }

  private static Pair<PsiClass, Integer> getTopOrStaticEnclosingClass(PsiClass aClass) {
    int depth = 0;
    PsiClass enclosing = PsiUtil.getContainingClass(aClass);
    while (enclosing != null) {
      depth++;
      if (enclosing.hasModifierProperty(PsiModifier.STATIC)) {
        break;
      }
      PsiClass next = PsiUtil.getContainingClass(enclosing);
      if (next == null) {
        break;
      }
      enclosing = next;
    }
    return Pair.create(enclosing, depth);
  }

  private @Nullable ReferenceType findNested(final ReferenceType fromClass, final int currentDepth, final PsiClass classToFind, final int requiredDepth, final SourcePosition position) {
    final VirtualMachineProxyImpl vmProxy = myDebugProcess.getVirtualMachineProxy();
    if (fromClass.isPrepared()) {
      // if the depth is still less than required - search nested classes recursively
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

      // reached the required depth - check if the position is contained within the fromClass locations
      int rangeBegin = Integer.MAX_VALUE;
      int rangeEnd = Integer.MIN_VALUE;
      List<Location> locations = DebuggerUtilsEx.allLineLocations(fromClass);
      if (locations != null) {
        for (Location location : locations) {
          final int lnumber = DebuggerUtilsEx.getLineNumber(location, false);
          if (lnumber <= 1) {
            // should be a native method, skipping
            // sometimes compiler generates location where line number is exactly 1 (e.g. GWT)
            // such locations are hardly correspond to real lines in code, so skipping them too
            continue;
          }
          final Method method = DebuggerUtilsEx.getMethod(location);
          if (method == null || DebuggerUtils.isSynthetic(method) || method.isBridge()) {
            // do not take into account synthetic stuff
            continue;
          }
          int locationLine = lnumber - 1;
          PsiFile psiFile = position.getFile().getOriginalFile();
          if (psiFile instanceof PsiCompiledFile) {
            locationLine = DebuggerUtilsEx.bytecodeToSourceLine(psiFile, locationLine);
            if (locationLine < 0) continue;
          }
          rangeBegin = Math.min(rangeBegin, locationLine);
          rangeEnd = Math.max(rangeEnd, locationLine);
        }
      }

      final int positionLine = position.getLine();
      if (positionLine >= rangeBegin && positionLine <= rangeEnd) {
        int finalRangeEnd = rangeEnd;
        Set<PsiClass> lineClasses = ReadAction.compute(() -> {
          // Now we use the last line to find the class, previously it was:
          // choose the second line to make sure that only this class' code exists on the line chosen
          // Otherwise the line (depending on the offset in it) can contain code that belongs to different classes
          // and JVMNameUtil.getClassAt(candidatePosition) will return the wrong class.
          // Example of such line:
          // list.add(new Runnable(){......
          // First offsets belong to parent class, and offsets inside te substring "new Runnable(){" belong to anonymous runnable.
          if (!classToFind.isValid()) {
            return Collections.emptySet();
          }
          return getLineClasses(position.getFile(), finalRangeEnd);
        });
        if (lineClasses.contains(classToFind)) {
          return fromClass;
        }
        return null;
      }
    }
    return null;
  }

  public @Nullable PsiMethod findMethod(PsiElement container, String className, String methodName, String methodSignature) {
    MethodFinder finder = new MethodFinder(className, methodName, methodSignature);
    container.accept(finder);
    return finder.getCompiledMethod();
  }

  //don't use JavaRecursiveElementWalkingVisitor because getNextSibling() works slowly for compiled elements
  private class MethodFinder extends JavaRecursiveElementVisitor {
    private final String myClassName;
    private PsiClass myCompiledClass;
    private final String myMethodName;
    private final String myMethodSignature;
    private PsiMethod myCompiledMethod;

    MethodFinder(final String className, final String methodName, final String methodSignature) {
      myClassName = className;
      myMethodName = methodName;
      myMethodSignature = methodSignature;
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (myCompiledMethod == null) {
        if (ContainerUtil.exists(getClassReferences(aClass, SourcePosition.createFromElement(aClass)),
                                 referenceType -> referenceType.name().equals(myClassName))) {
          myCompiledClass = aClass;
        }

        aClass.acceptChildren(this);
      }
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (myCompiledMethod == null) {
        try {
          PsiClass containingClass = method.getContainingClass();

          if (containingClass != null &&
              containingClass.equals(myCompiledClass) &&
              JVMNameUtil.getJVMMethodName(method).equals(myMethodName) &&
              checkSignature(method)) {
            myCompiledMethod = method;
          }
        }
        catch (EvaluateException e) {
          LOG.debug(e);
        }
      }
    }

    private boolean checkSignature(@NotNull PsiMethod method) throws EvaluateException {
      try {
        return JVMNameUtil.getJVMSignature(method).getName(myDebugProcess).equals(myMethodSignature);
      }
      catch (IndexNotReadyException e) {
        return true; // fallback: do not care about the signature
      }
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (myCompiledMethod == null) {
        super.visitElement(element);
      }
    }

    public @Nullable PsiMethod getCompiledMethod() {
      return myCompiledMethod;
    }
  }

  public static final class ClsSourcePosition extends RemappedSourcePosition {
    private final int myOriginalLine;

    public ClsSourcePosition(SourcePosition delegate, int originalLine) {
      super(delegate);
      myOriginalLine = originalLine;
    }

    @Override
    public SourcePosition mapDelegate(SourcePosition original) {
      PsiFile file = getFile();
      if (myOriginalLine < 0 || !file.isValid()) return original;
      file.getViewProvider().getDocument(); // to ensure decompilation
      SourcePosition position = calcLineMappedSourcePosition(file, myOriginalLine);
      return position != null ? position : original;
    }
  }

  private static @Nullable SourcePosition calcLineMappedSourcePosition(PsiFile psiFile, int originalLine) {
    int line = DebuggerUtilsEx.bytecodeToSourceLine(psiFile, originalLine);
    if (line > -1) {
      return SourcePosition.createFromLine(psiFile, line);
    }
    return null;
  }
}
