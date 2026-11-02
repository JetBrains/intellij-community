// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.MultiRequestPositionManager
import com.intellij.debugger.NoDataException
import com.intellij.debugger.PositionManagerAsync
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebuggerManagerThreadImpl.Companion.assertIsManagerThread
import com.intellij.debugger.engine.LambdaOrdinalResolver.findLambdaOrdinalAsync
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
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
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
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.Processor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
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

open class PositionManagerImpl(private val myDebugProcess: DebugProcessImpl) : PositionManagerAsync, MultiRequestPositionManager {
  open fun getDebugProcess(): DebugProcess = myDebugProcess

  @get:JvmName("getDebugProcessForKotlin")
  @get:JvmSynthetic
  val debugProcess: DebugProcess
    get() = getDebugProcess()

  @Throws(NoDataException::class)
  override fun locationsOfLine(type: ReferenceType, position: SourcePosition): List<Location> {
    val line = runReadActionBlocking { position.line + 1 }
    try {
      return DebuggerUtilsAsync.locationsOfLineSync(type, DebugProcess.JAVA_STRATUM, null, line)
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
    val requests = runReadActionBlocking {
      val res = ArrayList<Pair<ClassPrepareRequestor, String>>()
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
        res.add(prepareRequestor to classPattern)
      }
      res
    }
    return requests.mapNotNull { [prepareRequestor, classPattern] ->
      myDebugProcess.requestsManager.createClassPrepareRequest(prepareRequestor, classPattern)
    }
  }

  @RequiresBackgroundThread
  override suspend fun getSourcePositionAsync(location: Location?): SourcePosition? {
    assertIsManagerThread()
    if (location == null) {
      return null
    }

    val project = debugProcess.project
    @Suppress("ForbiddenInSuspectContextMethod")
    val originalFile = getPsiFileByLocation(project, location)
    if (originalFile == null) {
      return null
    }

    val lineNumber = DebuggerUtilsEx.getLineNumber(location, true)

    val qName = location.declaringType().name()

    val psiFile = readAction {
      if (!originalFile.isValid) return@readAction null
      val altFileUrl = DebuggerUtilsEx.getAlternativeSourceUrl(qName, project)
      val altFile = altFileUrl?.let { VirtualFileManager.getInstance().findFileByUrl(it) }
      altFile?.let { originalFile.manager.findFile(it) } ?: originalFile
    } ?: return null

    var sourcePosition = readAction {
      if (psiFile.isValid && lineNumber > -1) calcLineMappedSourcePosition(psiFile, lineNumber) else null
    }

    val method = DebuggerUtilsEx.getMethod(location)

    if (sourcePosition == null && (psiFile is PsiCompiledElement || lineNumber < 0)) {
      val methodName = method?.name()
      val methodSignature = method?.signature()
      if (methodName != null && methodSignature != null) {
        val psiClass = readAction { findPsiClassByName(qName, null) }
        @Suppress("ForbiddenInSuspectContextMethod")
        val compiledMethod = findMethod(psiClass ?: psiFile, qName, methodName, methodSignature)
        if (compiledMethod != null) {
          sourcePosition = readAction {
            if (!compiledMethod.isValid) return@readAction null
            val position = SourcePosition.createFromElement(compiledMethod)
            if (position != null && lineNumber >= 0) ClsSourcePosition(position, lineNumber) else position
          }
        }
      }
      else {
        return readAction { if (psiFile.isValid) SourcePosition.createFromLine(psiFile, -1) else null }
      }
    }

    if (sourcePosition == null) {
      sourcePosition = readAction { if (psiFile.isValid) SourcePosition.createFromLine(psiFile, lineNumber) else null }
    }
    if (sourcePosition == null) return null

    val lambdaOrdinal = findLambdaOrdinalAsync(sourcePosition, method)

    val condRetPos = adjustPositionForConditionalReturnAsync(location, psiFile, lineNumber)
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

  @RequiresBlockingContext
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

    val psiFile = runReadActionBlocking {
      val altSource = Ref<PsiFile?>()
      val psiClass = findPsiClassByName(originalQName, Consumer { c: ClsClassImpl -> altSource.set(findAlternativeJreSourceFile(c)) })

      if (!altSource.isNull) {
        return@runReadActionBlocking altSource.get()
      }

      if (psiClass != null) {
        var element = psiClass.navigationElement
        // see IDEA-137167, prefer not compiled elements
        if (element is PsiCompiledElement) {
          val fileElement = psiClass.containingFile.navigationElement
          if (fileElement !is PsiCompiledElement) {
            element = fileElement
          }
        }
        element.containingFile
      }
      else null
    }
    if (psiFile != null) {
      return psiFile
    }

    val sourceName = try {
      refType.sourceName()
    }
    catch (_: AbsentInformationException) {
      return null
    }
    return runReadActionBlocking {
      FilenameIndex.getFilesByName(project, sourceName, GlobalSearchScope.allScope(project)).firstOrNull { file ->
        file is PsiJavaFile && PsiTreeUtil.findChildrenOfAnyType(file, PsiClass::class.java).any { cls ->
          originalQName == JVMNameUtil.getClassVMName(cls)
        }
      }
    }
  }

  @RequiresReadLock
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

  @RequiresReadLock
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
      if (!psiClass.isValid) return ClassInfo(false, 0, null)
      var isLocalOrAnonymous = false
      var requiredDepth = 0
      var className = JVMNameUtil.getNonAnonymousClassName(psiClass)
      if (className == null) {
        isLocalOrAnonymous = true
        val [topLevelClass, depth] = getTopOrStaticEnclosingClass(psiClass)
        if (topLevelClass != null) {
          val parentClassName = JVMNameUtil.getNonAnonymousClassName(topLevelClass)
          if (parentClassName != null) {
            requiredDepth = depth
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

    val [isLocalOrAnonymous, requiredDepth, className] = ReadAction.computeBlocking<ClassInfo, RuntimeException> { createClassInfo() }

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

      val locationLines = DebuggerUtilsEx.allLineLocations(fromClass).orEmpty().mapNotNull { location ->
        val lineNumber = DebuggerUtilsEx.getLineNumber(location, false)
        if (lineNumber <= 1) {
          // Native methods and some generated locations do not have a useful line number.
          return@mapNotNull null
        }
        val method = DebuggerUtilsEx.getMethod(location)
        if (method == null || DebuggerUtils.isSynthetic(method) || method.isBridge) {
          return@mapNotNull null
        }
        lineNumber - 1
      }
      val matches = runReadActionBlocking {
        if (!classToFind.isValid) return@runReadActionBlocking false
        val file = position.file
        if (!file.isValid) return@runReadActionBlocking false
        val originalFile = file.originalFile
        var rangeBegin = Int.MAX_VALUE
        var rangeEnd = Int.MIN_VALUE
        for (line in locationLines) {
          val sourceLine = if (originalFile is PsiCompiledFile) DebuggerUtilsEx.bytecodeToSourceLine(originalFile, line) else line
          if (sourceLine < 0) continue
          rangeBegin = min(rangeBegin, sourceLine)
          rangeEnd = max(rangeEnd, sourceLine)
        }
        // Use the last line to distinguish an anonymous class from its enclosing class on the first line.
        position.line in rangeBegin..rangeEnd && getLineClasses(file, rangeEnd).contains(classToFind)
      }
      if (matches) return fromClass
    }
    return null
  }

  @RequiresBlockingContext
  open fun findMethod(container: PsiElement, className: String?, methodName: String?, methodSignature: String?): PsiMethod? {
    data class MethodCandidate(
      val method: SmartPsiElementPointer<PsiMethod>,
      val containingClass: SmartPsiElementPointer<PsiClass>,
      val classPosition: SourcePosition?,
    )

    val candidates = runReadActionBlocking {
      val result = ArrayList<MethodCandidate>()
      if (container.isValid) {
        // JavaRecursiveElementWalkingVisitor.getNextSibling() is slow for compiled elements.
        container.accept(object : JavaRecursiveElementVisitor() {
          @RequiresReadLock
          override fun visitMethod(method: PsiMethod) {
            val containingClass = method.containingClass ?: return
            if (JVMNameUtil.getJVMMethodName(method) == methodName) {
              result.add(MethodCandidate(SmartPointerManager.createPointer(method),
                                         SmartPointerManager.createPointer(containingClass),
                                         SourcePosition.createFromElement(containingClass)))
            }
          }
        })
      }
      result
    }
    val matchingClasses = HashMap<SmartPsiElementPointer<PsiClass>, Boolean>()
    for ([methodPointer, classPointer, classPosition] in candidates) {
      val matches = matchingClasses.getOrPut(classPointer) {
        val psiClass = runReadActionBlocking { classPointer.element }
        psiClass != null && getClassReferences(psiClass, classPosition).any { it.name() == className }
      }
      if (!matches) continue
      try {
        val signature = runReadActionBlocking { methodPointer.element?.let { JVMNameUtil.getJVMSignature(it) } } ?: continue
        if (signature.getName(myDebugProcess) != methodSignature) continue
      }
      catch (_: IndexNotReadyException) {
        // Accept the method without a signature when the indices are unavailable.
      }
      catch (e: EvaluateException) {
        LOG.debug(e)
        continue
      }
      val method = runReadActionBlocking { methodPointer.element }
      if (method != null) return method
    }
    return null
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
    suspend fun adjustPositionForConditionalReturnAsync(location: Location, file: PsiFile, lineNumber: Int): SourcePosition? {
      if (!location.virtualMachine().canGetBytecodes()) return null
      val returnPointer = readAction {
        if (!file.isValid) return@readAction null
        JavaLineBreakpointType.findSingleConditionalReturn(file, lineNumber)?.let {
          SmartPointerManager.createPointer(it)
        }
      } ?: return null
      val method = DebuggerUtilsEx.getMethod(location) ?: return null
      val bytecodes = method.bytecodes()
      val bytecodeOffset = Math.toIntExact(location.codeIndex())
      // The final implicit return must not be treated as a conditional return.
      if (bytecodeOffset in 0 until bytecodes.size - 1) {
        val opcode = bytecodes[bytecodeOffset].toInt() and 0xFF
        if (opcode in Opcodes.IRETURN..Opcodes.RETURN) {
          return readAction {
            returnPointer.element?.let { SourcePosition.createFromOffset(it.containingFile, it.textOffset) }
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
      return enclosing to depth
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
