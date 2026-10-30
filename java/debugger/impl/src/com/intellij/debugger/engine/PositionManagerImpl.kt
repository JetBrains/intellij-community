// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.MultiRequestPositionManager
import com.intellij.debugger.NoDataException
import com.intellij.debugger.PositionManager
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebuggerManagerThreadImpl.Companion.assertIsManagerThread
import com.intellij.debugger.engine.LambdaOrdinalResolver.findLambdaOrdinal
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.impl.AlternativeJreClassFinder
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiCompiledFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.Processor
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.EDT
import com.intellij.xdebugger.XDebuggerUtil
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.org.objectweb.asm.Opcodes
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min

open class PositionManagerImpl(private val myDebugProcess: DebugProcessImpl) : PositionManager, MultiRequestPositionManager {
  open fun getDebugProcess(): DebugProcess = myDebugProcess

  @get:JvmName("getDebugProcessForKotlin")
  @get:JvmSynthetic
  val debugProcess: DebugProcess
    get() = getDebugProcess()

  @Throws(NoDataException::class)
  override fun locationsOfLine(type: ReferenceType, position: SourcePosition): List<Location> {
    try {
      return DebuggerUtilsAsync.locationsOfLineSync(type, DebugProcess.JAVA_STRATUM, null, position.getLine() + 1)
    }
    catch (_: AbsentInformationException) {
    }
    return emptyList()
  }

  @Throws(NoDataException::class)
  override fun createPrepareRequest(requestor: ClassPrepareRequestor, position: SourcePosition): ClassPrepareRequest? {
    throw IllegalStateException(
      "This class implements MultiRequestPositionManager, corresponding createPrepareRequests version should be used"
    )
  }

  @Throws(NoDataException::class)
  override fun createPrepareRequests(requestor: ClassPrepareRequestor, position: SourcePosition): List<ClassPrepareRequest> {
    return ReadAction.computeBlocking<List<ClassPrepareRequest>, RuntimeException> {
      val res = ArrayList<ClassPrepareRequest>()
      for (psiClass in getLineClasses(position.getFile(), position.getLine())) {
        var prepareRequestor = requestor
        var classPattern = JVMNameUtil.getNonAnonymousClassName(psiClass)
        if (classPattern == null) {
          val parent = JVMNameUtil.getTopLevelParentClass(psiClass)
          if (parent == null) {
            continue
          }
          val parentQName = JVMNameUtil.getNonAnonymousClassName(parent)
          if (parentQName == null) {
            continue
          }
          classPattern = "$parentQName*"
          prepareRequestor = ClassPrepareRequestor { debuggerProcess, referenceType ->
            if ((debuggerProcess as DebugProcessImpl).positionManager.getAllClasses(position).contains(referenceType)) {
              requestor.processClassPrepare(debuggerProcess, referenceType)
            }
          }
        }
        val request = myDebugProcess.requestsManager.createClassPrepareRequest(prepareRequestor, classPattern)
        if (request != null) {
          res.add(request)
        }
      }
      res
    }
  }

  @Throws(NoDataException::class)
  override fun getSourcePosition(location: Location?): SourcePosition? {
    assertIsManagerThread()
    if (location == null) {
      return null
    }

    val project = debugProcess.project
    var psiFile = getPsiFileByLocation(project, location)
    if (psiFile == null) {
      return null
    }

    val lineNumber = DebuggerUtilsEx.getLineNumber(location, true)

    val qName = location.declaringType().name()

    // replace file with alternative
    val altFileUrl = DebuggerUtilsEx.getAlternativeSourceUrl(qName, project)
    if (altFileUrl != null) {
      val altFile = VirtualFileManager.getInstance().findFileByUrl(altFileUrl)
      if (altFile != null) {
        val altPsiFile = psiFile.getManager().findFile(altFile)
        if (altPsiFile != null) {
          psiFile = altPsiFile
        }
      }
    }

    var sourcePosition: SourcePosition? = null
    if (lineNumber > -1) {
      sourcePosition = calcLineMappedSourcePosition(psiFile, lineNumber)
    }

    val method = DebuggerUtilsEx.getMethod(location)

    if (sourcePosition == null && (psiFile is PsiCompiledElement || lineNumber < 0)) {
      if (method?.name() != null && method.signature() != null) {
        val psiClass = findPsiClassByName(qName, null)
        val compiledMethod = findMethod(psiClass ?: psiFile, qName, method.name(), method.signature())
        if (compiledMethod != null) {
          sourcePosition = SourcePosition.createFromElement(compiledMethod)
          if (sourcePosition != null && lineNumber >= 0) {
            sourcePosition = ClsSourcePosition(sourcePosition, lineNumber)
          }
        }
      }
      else {
        return SourcePosition.createFromLine(psiFile, -1)
      }
    }

    if (sourcePosition == null) {
      sourcePosition = SourcePosition.createFromLine(psiFile, lineNumber)
    }

    val lambdaOrdinal = findLambdaOrdinal(sourcePosition, method)

    val condRetPos: SourcePosition? = adjustPositionForConditionalReturn(location, psiFile, lineNumber)
    if (condRetPos != null) {
      sourcePosition = condRetPos
    }

    return JavaSourcePosition(sourcePosition, location.declaringType(), method, lambdaOrdinal)
  }

  open class JavaSourcePosition : RemappedSourcePosition {
    private val myExpectedClassName: String?
    private val myExpectedMethodName: String?
    private val myLambdaOrdinal: Int

    constructor(delegate: SourcePosition, declaringType: ReferenceType?, method: Method?, lambdaOrdinal: Int) : super(delegate) {
      myExpectedClassName = declaringType?.name()
      myExpectedMethodName = method?.name()
      myLambdaOrdinal = lambdaOrdinal
    }

    constructor(delegate: SourcePosition, lambdaOrdinal: Int) : super(delegate) {
      assert(lambdaOrdinal > -1)
      myExpectedClassName = null
      myExpectedMethodName = "lambda$" // fake lambda name
      myLambdaOrdinal = lambdaOrdinal
    }

    private fun remapElement(element: PsiElement?): PsiElement? {
      val name = JVMNameUtil.getClassVMName(PsiUtil.getContainingClass(element))
      if (name != null && name != myExpectedClassName) {
        return null
      }
      val method = DebuggerUtilsEx.getContainingMethod(element)
      if (!StringUtil.isEmpty(myExpectedMethodName)) {
        if (method == null) {
          return null
        }
        else if (((method is PsiMethod && myExpectedMethodName == method.getName()) ||
                  (method is PsiLambdaExpression && DebuggerUtilsEx.isLambdaName(myExpectedMethodName))) &&
                 insideBody(element!!, DebuggerUtilsEx.getBody(method))
        ) {
          return element
        }
      }
      return null
    }

    override fun mapDelegate(original: SourcePosition): SourcePosition? {
      return ReadAction.nonBlocking<SourcePosition?> {
        val file = original.getFile()
        val line = original.getLine()

        val document = file.getViewProvider().getDocument()
        if (document == null || line >= document.getLineCount()) {
          return@nonBlocking original
        }

        if (DebuggerUtilsEx.isLambdaName(myExpectedMethodName) && myLambdaOrdinal > -1) {
          val lambdas = DebuggerUtilsEx.collectLambdas(original, true)

          if (myLambdaOrdinal < lambdas.size) {
            val firstElem = DebuggerUtilsEx.getFirstElementOnTheLine(lambdas[myLambdaOrdinal], document, line)
            if (firstElem != null) {
              return@nonBlocking createFromElement(firstElem)
            }
          }
        }
        else {
          // There may be more than one class/method code on the line, so we need to find out the correct place
          val res = Ref.create<SourcePosition?>()
          XDebuggerUtil.getInstance().iterateLine(file.project, document, line, Processor { elem: PsiElement? ->
            val remappedElement = remapElement(elem)
            if (remappedElement != null) {
              if (remappedElement.getTextOffset() > original.getOffset()) {
                res.set(createFromElement(remappedElement))
              }
              return@Processor false
            }
            true
          })
          if (!res.isNull) {
            return@nonBlocking res.get()
          }
        }
        original
      }.executeSynchronously()
    }

    companion object {
      private fun insideBody(element: PsiElement, body: PsiElement?): Boolean {
        if (!PsiTreeUtil.isAncestor(body, element, false)) return false
        if (body is PsiCodeBlock) {
          return element != body.getRBrace() && element != body.getLBrace()
        }
        return true
      }
    }
  }

  protected open fun getPsiFileByLocation(project: Project, location: Location): PsiFile? {
    val refType = location.declaringType()
    if (refType == null) {
      return null
    }

    // We should find a class no matter what
    // setAlternativeResolveEnabled is turned on here
    //if (DumbService.getInstance(project).isDumb()) {
    //  return null;
    //}
    val originalQName = refType.name()

    val altSource = Ref<PsiFile?>()
    val psiClass = findPsiClassByName(originalQName, Consumer { c: ClsClassImpl -> altSource.set(findAlternativeJreSourceFile(c)) })

    if (!altSource.isNull) {
      return altSource.get()
    }

    if (psiClass != null) {
      var element = psiClass.getNavigationElement()
      // see IDEA-137167, prefer not compiled elements
      if (element is PsiCompiledElement) {
        val fileElement = psiClass.getContainingFile().getNavigationElement()
        if (fileElement !is PsiCompiledElement) {
          element = fileElement
        }
      }
      return element.getContainingFile()
    }
    else {
      // try to search by filename
      try {
        val files = FilenameIndex.getFilesByName(project, refType.sourceName(), GlobalSearchScope.allScope(project))
        for (file in files) {
          if (file is PsiJavaFile) {
            for (cls in PsiTreeUtil.findChildrenOfAnyType<PsiClass?>(file, PsiClass::class.java)) {
              if (StringUtil.equals(originalQName, JVMNameUtil.getClassVMName(cls))) {
                return file
              }
            }
          }
        }
      }
      catch (_: AbsentInformationException) {
      }
    }

    return null
  }

  private fun findPsiClassByName(originalQName: String, altClsProcessor: Consumer<in ClsClassImpl>?): PsiClass? {
    var psiClass: PsiClass? = null
    // first check alternative jre if any
    val alternativeJre = myDebugProcess.session.alternativeJre
    if (alternativeJre != null) {
      val scope = AlternativeJreClassFinder.getSearchScope(alternativeJre)
      psiClass = findClass(myDebugProcess.project, originalQName, scope, false)
      if (psiClass is ClsClassImpl && altClsProcessor != null) { //try to find sources
        altClsProcessor.accept(psiClass)
      }
    }

    if (psiClass == null) {
      psiClass = findClass(myDebugProcess.project, originalQName, myDebugProcess.searchScope, true)
    }
    return psiClass
  }

  private fun findAlternativeJreSourceFile(psiClass: ClsClassImpl): PsiFile? {
    val sourceFileName = psiClass.getSourceFileName()
    val packageName = (psiClass.getContainingFile() as PsiClassOwner).getPackageName()
    val relativePath = if (packageName.isEmpty()) sourceFileName else packageName.replace('.', '/') + '/' + sourceFileName
    val alternativeJre = myDebugProcess.session.alternativeJre

    if (alternativeJre != null) {
      for (file in AlternativeJreClassFinder.getSourceRoots(alternativeJre)) {
        val source = file.findFileByRelativePath(relativePath)
        if (source != null && source.isValid()) {
          val psiSource = psiClass.getManager().findFile(source)
          if (psiSource is PsiClassOwner) {
            return psiSource
          }
        }
      }
    }
    return null
  }

  @Throws(NoDataException::class)
  override fun getAllClasses(position: SourcePosition): @Unmodifiable List<ReferenceType> {
    val lineClasses = ReadAction.computeBlocking<Set<PsiClass>, RuntimeException> { getLineClasses(position.getFile(), position.getLine()) }
    return ContainerUtil.flatMap(lineClasses) { aClass -> getClassReferences(aClass, position) }
  }

  private fun getClassReferences(psiClass: PsiClass, position: SourcePosition?): List<ReferenceType> {
    data class ClassInfo(val isLocalOrAnonymous: Boolean, val requiredDepth: Int, val className: String?)

    @RequiresReadLock
    fun createClassInfo(): ClassInfo {
      var isLocalOrAnonymous = false
      var requiredDepth = 0
      var className = JVMNameUtil.getNonAnonymousClassName(psiClass)
      if (className == null) {
        isLocalOrAnonymous = true
        val enclosing = getTopOrStaticEnclosingClass(psiClass)
        val topLevelClass = enclosing.first
        if (topLevelClass != null) {
          val parentClassName = JVMNameUtil.getNonAnonymousClassName(topLevelClass)
          if (parentClassName != null) {
            requiredDepth = enclosing.second
            className = parentClassName
          }
        }
        else {
          val sb = StringBuilder()
          PsiTreeUtil.treeWalkUp(psiClass, null) { element, _ ->
            sb.append('\n').append(element)
            true
          }
          LOG.info("Local or anonymous class $psiClass has no non-local parent, parents:$sb")
        }
      }
      return ClassInfo(isLocalOrAnonymous, requiredDepth, className)
    }

    val (isLocalOrAnonymous, requiredDepth, className) = ReadAction.computeBlocking<ClassInfo, RuntimeException> { createClassInfo() }

    if (className == null) {
      return emptyList()
    }

    val matchingClasses = VirtualMachineProxyImpl.getCurrent().classesByName(className)
    if (!isLocalOrAnonymous) {
      return matchingClasses
    }

    if (position == null) {
      return emptyList()
    }

    if (matchingClasses.isEmpty()) { // sometimes inner classes may be loaded before outer
      return VirtualMachineProxyImpl.getCurrent().allClasses().asSequence()
        .filter { t -> t.name().startsWith(className) }
        .mapNotNull { outer -> findNested(outer, 0, psiClass, 0, position) }
        .toList()
    }

    return matchingClasses.mapNotNull { outer -> findNested(outer, 0, psiClass, requiredDepth, position) }
  }

  private fun findNested(
    fromClass: ReferenceType,
    currentDepth: Int,
    classToFind: PsiClass,
    requiredDepth: Int,
    position: SourcePosition,
  ): ReferenceType? {
    val vmProxy = VirtualMachineProxyImpl.getCurrent()
    if (fromClass.isPrepared) {
      // if the depth is still less than required - search nested classes recursively
      if (currentDepth < requiredDepth) {
        val nestedTypes = vmProxy.nestedTypes(fromClass)
        for (nested in nestedTypes) {
          val found = findNested(nested, currentDepth + 1, classToFind, requiredDepth, position)
          if (found != null) {
            return found
          }
        }
        return null
      }

      // reached the required depth - check if the position is contained within the fromClass locations
      var rangeBegin = Int.MAX_VALUE
      var rangeEnd = Int.MIN_VALUE
      val locations = DebuggerUtilsEx.allLineLocations(fromClass)
      if (locations != null) {
        for (location in locations) {
          val lnumber = DebuggerUtilsEx.getLineNumber(location, false)
          if (lnumber <= 1) {
            // should be a native method, skipping
            // sometimes compiler generates location where line number is exactly 1 (e.g. GWT)
            // such locations are hardly correspond to real lines in code, so skipping them too
            continue
          }
          val method = DebuggerUtilsEx.getMethod(location)
          if (method == null || DebuggerUtils.isSynthetic(method) || method.isBridge) {
            // do not take into account synthetic stuff
            continue
          }
          var locationLine = lnumber - 1
          val psiFile = position.getFile().getOriginalFile()
          if (psiFile is PsiCompiledFile) {
            locationLine = DebuggerUtilsEx.bytecodeToSourceLine(psiFile, locationLine)
            if (locationLine < 0) continue
          }
          rangeBegin = min(rangeBegin, locationLine)
          rangeEnd = max(rangeEnd, locationLine)
        }
      }

      val positionLine = position.getLine()
      if (positionLine in rangeBegin..rangeEnd) {
        val finalRangeEnd = rangeEnd
        val lineClasses = ReadAction.computeBlocking<Set<PsiClass>, RuntimeException> {
          // Now we use the last line to find the class, previously it was:
          // choose the second line to make sure that only this class' code exists on the line chosen
          // Otherwise the line (depending on the offset in it) can contain code that belongs to different classes
          // and JVMNameUtil.getClassAt(candidatePosition) will return the wrong class.
          // Example of such line:
          // list.add(new Runnable(){......
          // First offsets belong to parent class, and offsets inside te substring "new Runnable(){" belong to anonymous runnable.
          if (!classToFind.isValid()) {
            return@computeBlocking emptySet()
          }
          getLineClasses(position.getFile(), finalRangeEnd)
        }
        if (lineClasses.contains(classToFind)) {
          return fromClass
        }
        return null
      }
    }
    return null
  }

  open fun findMethod(container: PsiElement, className: String?, methodName: String?, methodSignature: String?): PsiMethod? {
    val finder = MethodFinder(className, methodName, methodSignature)
    container.accept(finder)
    return finder.compiledMethod
  }

  //don't use JavaRecursiveElementWalkingVisitor because getNextSibling() works slowly for compiled elements
  private inner class MethodFinder(
    private val myClassName: String?,
    private val myMethodName: String?,
    private val myMethodSignature: String?,
  ) : JavaRecursiveElementVisitor() {
    private var myCompiledClass: PsiClass? = null
    var compiledMethod: PsiMethod? = null
      private set

    override fun visitClass(aClass: PsiClass) {
      if (this.compiledMethod == null) {
        if (getClassReferences(aClass, SourcePosition.createFromElement(aClass)).any { it.name() == myClassName }
        ) {
          myCompiledClass = aClass
        }

        aClass.acceptChildren(this)
      }
    }

    override fun visitMethod(method: PsiMethod) {
      if (this.compiledMethod == null) {
        try {
          val containingClass = method.getContainingClass()

          if (containingClass != null &&
              containingClass == myCompiledClass &&
              JVMNameUtil.getJVMMethodName(method) == myMethodName &&
              checkSignature(method)
          ) {
            this.compiledMethod = method
          }
        }
        catch (e: EvaluateException) {
          LOG.debug(e)
        }
      }
    }

    @Throws(EvaluateException::class)
    fun checkSignature(method: PsiMethod): Boolean {
      try {
        return JVMNameUtil.getJVMSignature(method).getName(myDebugProcess) == myMethodSignature
      }
      catch (_: IndexNotReadyException) {
        return true // fallback: do not care about the signature
      }
    }

    override fun visitElement(element: PsiElement) {
      if (this.compiledMethod == null) {
        super.visitElement(element)
      }
    }
  }

  class ClsSourcePosition(delegate: SourcePosition, private val myOriginalLine: Int) : RemappedSourcePosition(delegate) {
    override fun mapDelegate(original: SourcePosition?): SourcePosition? {
      val file = getFile()
      if (myOriginalLine < 0 || !file.isValid()) return original

      if (`is`("hyperlink.ide.decompiler.open.file") &&
          EDT.isCurrentThreadEdt() && !ApplicationManager.getApplication().isWriteAccessAllowed() &&
          BinaryFileTypeDecompilers.getInstance().hasDecompiler(file.getVirtualFile())
      ) {
        ProgressManager.getInstance()
          .runProcessWithProgressSynchronously<Document?, RuntimeException>(
            ThrowableComputable {
              ReadAction.computeCancellable<Document?, RuntimeException> { file.viewProvider.document }
            },
            IdeBundle.message("progress.title.preparing.navigation"), true, file.project
          )
      }
      else {
        file.getViewProvider().getDocument() // to ensure decompilation
      }
      val position: SourcePosition? = calcLineMappedSourcePosition(file, myOriginalLine)
      return position ?: original
    }
  }

  companion object {
    private val LOG = Logger.getInstance(PositionManagerImpl::class.java)

    @ApiStatus.Internal
    @JvmStatic
    fun adjustPositionForConditionalReturn(location: Location, file: PsiFile, lineNumber: Int): SourcePosition? {
      if (location.virtualMachine().canGetBytecodes()) {
        val ret = JavaLineBreakpointType.findSingleConditionalReturn(file, lineNumber)
        if (ret != null) {
          val bytecodes = DebuggerUtilsEx.getMethod(location).bytecodes()
          val bytecodeOffs = Math.toIntExact(location.codeIndex())
          // Implicit return instruction at the end of bytecode should not be treated as conditional return.
          // (Note that we also relay on the fact that all return instructions have no operands.)
          if (0 <= bytecodeOffs && bytecodeOffs < bytecodes.size - 1) {
            val opcode = bytecodes[bytecodeOffs].toInt() and 0xFF
            if (Opcodes.IRETURN <= opcode && opcode <= Opcodes.RETURN) {
              return ReadAction.computeBlocking<SourcePosition?, RuntimeException> {
                SourcePosition.createFromOffset(
                  file,
                  ret.getTextOffset()
                )
              }
            }
          }
        }
      }
      return null
    }

    @RequiresReadLock
    @JvmStatic
    protected fun getLineClasses(file: PsiFile, lineNumber: Int): MutableSet<PsiClass> {
      val document = file.getViewProvider().getDocument()
      val res = HashSet<PsiClass>()
      if (document != null) {
        XDebuggerUtil.getInstance().iterateLine(file.project, document, lineNumber, Processor { element: PsiElement? ->
          val aClass = PsiUtil.getContainingClass(element)
          if (aClass != null) {
            res.add(aClass)
          }
          true
        })
      }
      return res
    }

    @JvmStatic
    fun findClass(project: Project, originalQName: String, searchScope: GlobalSearchScope, fallbackToAllScope: Boolean): PsiClass? {
      var psiClass =
        DebuggerUtils.findClass(originalQName, project, searchScope, fallbackToAllScope) // try to lookup original name first
      if (psiClass == null) {
        val dollar = originalQName.indexOf('$')
        if (dollar > 0) {
          psiClass = DebuggerUtils.findClass(originalQName.substring(0, dollar), project, searchScope, fallbackToAllScope)
        }
      }
      return psiClass
    }

    private fun getTopOrStaticEnclosingClass(aClass: PsiClass): Pair<PsiClass?, Int> {
      var depth = 0
      var enclosing = PsiUtil.getContainingClass(aClass)
      while (enclosing != null) {
        depth++
        if (enclosing.hasModifierProperty(PsiModifier.STATIC)) {
          break
        }
        val next = PsiUtil.getContainingClass(enclosing)
        if (next == null) {
          break
        }
        enclosing = next
      }
      return Pair.create(enclosing, depth)
    }

    private fun calcLineMappedSourcePosition(psiFile: PsiFile, originalLine: Int): SourcePosition? {
      val line = DebuggerUtilsEx.bytecodeToSourceLine(psiFile, originalLine)
      if (line > -1) {
        return SourcePosition.createFromLine(psiFile, line)
      }
      return null
    }
  }
}
