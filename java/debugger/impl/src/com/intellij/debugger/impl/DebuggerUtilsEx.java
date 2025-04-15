// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class DebuggerUtilsEx
 * @author Jeka
 */
package com.intellij.debugger.impl;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.UnBoxingEvaluator;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.jdi.GeneratedLocation;
import com.intellij.debugger.jdi.GeneratedReferenceType;
import com.intellij.debugger.jdi.JvmtiError;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.memory.ui.CollectionHistoryView;
import com.intellij.debugger.requests.Requestor;
import com.intellij.debugger.settings.DebuggerSettingsUtils;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.FieldDescriptor;
import com.intellij.execution.filters.ExceptionFilters;
import com.intellij.execution.filters.LineNumbersMapping;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.threadDumpParser.ThreadState;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.content.Content;
import com.intellij.ui.viewModel.extraction.ToolWindowContentExtractor;
import com.intellij.unscramble.DumpItem;
import com.intellij.unscramble.JavaThreadDumpItem;
import com.intellij.unscramble.ThreadDumpPanel;
import com.intellij.util.DocumentUtil;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.jetbrains.jdi.ArrayReferenceImpl;
import com.jetbrains.jdi.JNITypeParser;
import com.jetbrains.jdi.LocationImpl;
import com.jetbrains.jdi.ObjectReferenceImpl;
import com.sun.jdi.*;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import one.util.streamex.StreamEx;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public abstract class DebuggerUtilsEx extends DebuggerUtils {
  private static final Logger LOG = Logger.getInstance(DebuggerUtilsEx.class);

  /**
   * @return all CodeFragmentFactoryProviders that provide code fragment factories suitable in the context given
   */
  public static List<CodeFragmentFactory> getCodeFragmentFactories(@Nullable PsiElement context) {
    DefaultCodeFragmentFactory defaultFactory = DefaultCodeFragmentFactory.getInstance();
    List<CodeFragmentFactory> suitableFactories = new SmartList<>();
    CodeFragmentFactory.EXTENSION_POINT_NAME.forEachExtensionSafe(factory -> {
      if (factory != defaultFactory && factory.isContextAccepted(context)) {
        suitableFactories.add(factory);
      }
    });
    suitableFactories.add(defaultFactory); // let default factory be the last one
    return suitableFactories;
  }

  public static PsiMethod findPsiMethod(PsiFile file, int offset) {
    PsiElement element = null;

    while (offset >= 0) {
      element = file.findElementAt(offset);
      if (element != null) {
        break;
      }
      offset--;
    }

    for (; element != null; element = element.getParent()) {
      if (element instanceof PsiClass || element instanceof PsiLambdaExpression) {
        return null;
      }
      if (element instanceof PsiMethod) {
        return (PsiMethod)element;
      }
    }
    return null;
  }

  /**
   * Does not handle array types correctly
   * @deprecated use {@link DebuggerUtils#instanceOf(Type, String)}
   */
  @Deprecated
  public static boolean isAssignableFrom(@NotNull String baseQualifiedName, @NotNull Type checkedType) {
    if (checkedType instanceof ReferenceType) {
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(baseQualifiedName)) {
        return true;
      }
      return getSuperClass(baseQualifiedName, (ReferenceType)checkedType) != null;
    }
    return baseQualifiedName.equals(checkedType.name());
  }

  @Deprecated
  public static ReferenceType getSuperClass(final @NotNull String baseQualifiedName, @NotNull ReferenceType checkedType) {
    if (baseQualifiedName.equals(checkedType.name())) {
      return checkedType;
    }

    if (checkedType instanceof ClassType classType) {
      ClassType superClassType = classType.superclass();
      if (superClassType != null) {
        ReferenceType superClass = getSuperClass(baseQualifiedName, superClassType);
        if (superClass != null) {
          return superClass;
        }
      }
      List<InterfaceType> interfaces = classType.allInterfaces();
      for (InterfaceType iface : interfaces) {
        ReferenceType superClass = getSuperClass(baseQualifiedName, iface);
        if (superClass != null) {
          return superClass;
        }
      }
    }

    if (checkedType instanceof InterfaceType) {
      List<InterfaceType> list = ((InterfaceType)checkedType).superinterfaces();
      for (InterfaceType superInterface : list) {
        ReferenceType superClass = getSuperClass(baseQualifiedName, superInterface);
        if (superClass != null) {
          return superClass;
        }
      }
    }
    return null;
  }

  public static ClassFilter create(Element element) throws InvalidDataException {
    return DebuggerSettingsUtils.create(element);
  }

  private static boolean isFiltered(ClassFilter classFilter, String qName) {
    if (!classFilter.isEnabled()) {
      return false;
    }
    try {
      if (classFilter.matches(qName)) {
        return true;
      }
    }
    catch (PatternSyntaxException e) {
      LOG.debug(e);
    }
    return false;
  }

  public static boolean isFiltered(@NotNull String qName, ClassFilter[] classFilters) {
    return isFiltered(qName, Arrays.asList(classFilters));
  }

  public static boolean isFiltered(@NotNull String qName, List<? extends ClassFilter> classFilters) {
    if (qName.indexOf('[') != -1) {
      return false; //is array
    }

    return ContainerUtil.exists(classFilters, filter -> isFiltered(filter, qName));
  }

  public static int getEnabledNumber(ClassFilter[] classFilters) {
    return (int)Arrays.stream(classFilters).filter(ClassFilter::isEnabled).count();
  }

  /**
   * @deprecated Use {@link DebuggerSettingsUtils#readFilters} directly
   */
  @Deprecated
  public static ClassFilter[] readFilters(List<? extends Element> children) {
    return DebuggerSettingsUtils.readFilters(children);
  }

  /**
   * @deprecated Use {@link DebuggerSettingsUtils#writeFilters} directly
   */
  @Deprecated
  public static void writeFilters(@NotNull Element parentNode,
                                  @NonNls String tagName,
                                  ClassFilter[] filters) throws WriteExternalException {
    DebuggerSettingsUtils.writeFilters(parentNode, tagName, filters);
  }

  /**
   * @deprecated Use {@link DebuggerSettingsUtils#filterEquals} directly
   */
  @Deprecated
  public static boolean filterEquals(ClassFilter[] filters1, ClassFilter[] filters2) {
    return DebuggerSettingsUtils.filterEquals(filters1, filters2);
  }

  private static boolean elementListsEqual(List<? extends Element> l1, List<? extends Element> l2) {
    if (l1 == null) return l2 == null;
    if (l2 == null) return false;

    if (l1.size() != l2.size()) return false;

    Iterator<? extends Element> i1 = l1.iterator();

    for (Element aL2 : l2) {
      Element elem1 = i1.next();

      if (!elementsEqual(elem1, aL2)) return false;
    }
    return true;
  }

  private static boolean attributeListsEqual(List<? extends Attribute> l1, List<? extends Attribute> l2) {
    if (l1 == null) return l2 == null;
    if (l2 == null) return false;

    if (l1.size() != l2.size()) return false;

    Iterator<? extends Attribute> i1 = l1.iterator();

    for (Attribute aL2 : l2) {
      Attribute attr1 = i1.next();

      if (!Objects.equals(attr1.getName(), aL2.getName()) || !Objects.equals(attr1.getValue(), aL2.getValue())) {
        return false;
      }
    }
    return true;
  }

  public static boolean elementsEqual(Element e1, Element e2) {
    if (e1 == null) {
      return e2 == null;
    }
    if (!Objects.equals(e1.getName(), e2.getName())) {
      return false;
    }
    if (!elementListsEqual(e1.getChildren(), e2.getChildren())) {
      return false;
    }
    if (!attributeListsEqual(e1.getAttributes(), e2.getAttributes())) {
      return false;
    }
    return true;
  }

  public static boolean externalizableEqual(JDOMExternalizable e1, JDOMExternalizable e2) {
    Element root1 = new Element("root");
    Element root2 = new Element("root");
    try {
      e1.writeExternal(root1);
    }
    catch (WriteExternalException e) {
      LOG.debug(e);
    }
    try {
      e2.writeExternal(root2);
    }
    catch (WriteExternalException e) {
      LOG.debug(e);
    }

    return elementsEqual(root1, root2);
  }

  public static @NotNull List<Pair<Breakpoint, Event>> getEventDescriptors(@Nullable SuspendContextImpl suspendContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (suspendContext != null) {
      EventSet events = suspendContext.getEventSet();
      if (!ContainerUtil.isEmpty(events)) {
        List<Pair<Breakpoint, Event>> eventDescriptors = new SmartList<>();
        for (Event event : events) {
          Requestor requestor = RequestManagerImpl.findRequestor(event.request());
          if (requestor instanceof Breakpoint) {
            eventDescriptors.add(Pair.create((Breakpoint)requestor, event));
          }
        }
        return eventDescriptors;
      }
    }
    return Collections.emptyList();
  }

  private static int myThreadDumpsCount = 0;

  @ApiStatus.Internal
  public static ThreadDumpPanel createThreadDumpPanel(Project project, List<DumpItem> dumpItems, RunnerLayoutUi ui, GlobalSearchScope searchScope) {
    final TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    consoleBuilder.filters(ExceptionFilters.getFilters(searchScope));
    final ConsoleView consoleView = consoleBuilder.getConsole();
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    consoleView.allowHeavyFilters();
    final ThreadDumpPanel panel = ThreadDumpPanel.createFromDumpItems(project, consoleView, toolbarActions, dumpItems);

    String id = JavaDebuggerBundle.message("thread.dump.name", DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis()));
    final Content content = ui.createContent(id + " " + myThreadDumpsCount, panel, id, null, null);
    content.putUserData(RunnerContentUi.LIGHTWEIGHT_CONTENT_MARKER, Boolean.TRUE);
    content.setCloseable(true);
    content.setDescription(JavaDebuggerBundle.message("thread.dump"));
    content.putUserData(ToolWindowContentExtractor.SYNC_TAB_TO_REMOTE_CLIENTS, true);
    ui.addContent(content);
    ui.selectAndFocus(content, true, true);
    myThreadDumpsCount++;
    Disposer.register(content, consoleView);
    ui.selectAndFocus(content, true, false);
    if (!dumpItems.isEmpty()) {
      panel.selectStackFrame(0);
    }
    return panel;
  }

  @ApiStatus.Internal
  public static void addDumpItems(Project project, List<DumpItem> dumpItems, RunnerLayoutUi ui, GlobalSearchScope searchScope) {
    createThreadDumpPanel(project, dumpItems, ui, searchScope);
  }

  public static void addThreadDump(Project project, List<ThreadState> threads, RunnerLayoutUi ui, GlobalSearchScope searchScope) {
    List<DumpItem> javaThreadDump = new ArrayList<>(ContainerUtil.map(threads, JavaThreadDumpItem::new));
    addDumpItems(project, javaThreadDump, ui, searchScope);
  }

  public static void addCollectionHistoryTab(@NotNull XDebugSession session, @NotNull XValueNodeImpl node) {
    XValueContainer container = node.getValueContainer();
    if (container instanceof JavaValue) {
      ValueDescriptorImpl descriptor = ((JavaValue)container).getDescriptor();
      if (descriptor instanceof FieldDescriptor) {
        Field field = ((FieldDescriptor)descriptor).getField();
        String clsName = field.declaringType().name().replace("$", ".");
        String fieldName = field.name();
        addCollectionHistoryTab(session, clsName, fieldName, node);
      }
    }
  }

  public static void addCollectionHistoryTab(@NotNull XDebugSession session,
                                             @NotNull String clsName,
                                             @NotNull String fieldName,
                                             @Nullable XValueNodeImpl node) {
    XDebugProcess process = session.getDebugProcess();
    RunnerLayoutUi ui = session.getUI();
    String title = JavaDebuggerBundle.message("collection.history.tab.title", clsName + "." + fieldName);
    for (Content content : ui.getContents()) {
      if (title.equals(content.getDisplayName())) {
        ui.removeContent(content, true);
      }
    }
    JComponent view = new CollectionHistoryView(clsName, fieldName, process, node).getComponent();
    Content content = ui.createContent(title, view, title, null, null);
    content.setCloseable(true);
    content.setDescription(JavaDebuggerBundle.message("collection.history"));
    ui.addContent(content);
    ui.selectAndFocus(content, true, true);
  }

  public static StringReference mirrorOfString(@NotNull String s, @NotNull EvaluationContext context)
    throws EvaluateException {
    return mirrorOfString(s, ((SuspendContextImpl)context.getSuspendContext()).getVirtualMachineProxy(), context);
  }

  /**
   * @deprecated use {@link #mirrorOfString(String, EvaluationContext)}
   */
  @Deprecated
  public static StringReference mirrorOfString(@NotNull String s, VirtualMachineProxyImpl virtualMachineProxy, EvaluationContext context)
    throws EvaluateException {
    return context.computeAndKeep(() -> virtualMachineProxy.mirrorOf(s));
  }

  public static @NotNull ArrayReference mirrorOfArray(@NotNull ArrayType arrayType, int dimension, EvaluationContext context)
    throws EvaluateException {
    return context.computeAndKeep(() -> context.getDebugProcess().newInstance(arrayType, dimension));
  }

  public static @NotNull ArrayReference mirrorOfArray(@NotNull ArrayType arrayType,
                                                      @NotNull List<? extends Value> values,
                                                      @NotNull EvaluationContext context)
    throws EvaluateException {
    ArrayReference res = context.computeAndKeep(() -> context.getDebugProcess().newInstance(arrayType, values.size()));
    try {
      setArrayValues(res, values, false);
    }
    catch (Exception e) {
      throw new EvaluateException(e.getMessage(), e);
    }
    return res;
  }

  public static @NotNull ArrayReference mirrorOfByteArray(byte[] bytes, EvaluationContext context)
    throws EvaluateException, InvalidTypeException, ClassNotLoadedException {
    context = ((EvaluationContextImpl)context).withAutoLoadClasses(true);
    ArrayType arrayClass = (ArrayType)context.getDebugProcess().findClass(context, "byte[]", context.getClassLoader());
    ArrayReference reference = mirrorOfArray(arrayClass, bytes.length, context);
    VirtualMachine virtualMachine = reference.virtualMachine();
    List<Value> mirrors = new ArrayList<>(bytes.length);
    for (byte b : bytes) {
      mirrors.add(virtualMachine.mirrorOf(b));
    }
    setArrayValues(reference, mirrors, false);
    return reference;
  }

  public static void setArrayValues(@NotNull ArrayReference array, @NotNull List<? extends Value> values, boolean checkAssignable)
    throws ClassNotLoadedException, InvalidTypeException {

    // The comment below is a workaround for QD-10948.
    //noinspection ConstantValue
    if (array instanceof ArrayReferenceImpl) {
      ((ArrayReferenceImpl)array).setValues(0, values, 0, -1, checkAssignable);
    }
    else {
      array.setValues(values);
    }
  }

  public static @NotNull CodeFragmentFactory getCodeFragmentFactory(@Nullable PsiElement context, @Nullable FileType fileType) {
    DefaultCodeFragmentFactory defaultFactory = DefaultCodeFragmentFactory.getInstance();
    if (fileType == null) {
      if (context == null) {
        return defaultFactory;
      }
      else {
        PsiFile file = context.getContainingFile();
        fileType = file != null ? file.getFileType() : null;
      }
    }
    for (CodeFragmentFactory factory : CodeFragmentFactory.EXTENSION_POINT_NAME.getExtensionList()) {
      if (factory != defaultFactory && (fileType == null || factory.getFileType().equals(fileType)) && factory.isContextAccepted(context)) {
        return factory;
      }
    }
    return defaultFactory;
  }

  public static @NotNull CodeFragmentFactory findAppropriateCodeFragmentFactory(final TextWithImports text, final PsiElement context) {
    CodeFragmentFactory factory = ReadAction.compute(() -> getCodeFragmentFactory(context, text.getFileType()));
    return new CodeFragmentFactoryContextWrapper(factory);
  }

  /**
   * @param location location to get the assertion status for
   * @return the effective assertion status at given code location:
   * {@link ThreeState#YES} means assertions are enabled
   * {@link ThreeState#NO} means assertions are disabled
   * {@link ThreeState#UNSURE} means there are no assertions in the current class, so the status was not requested by the runtime
   */
  public static @NotNull ThreeState getEffectiveAssertionStatus(@NotNull Location location) {
    ReferenceType type = location.declaringType();
    if (type instanceof ClassType) {
      Field field = DebuggerUtils.findField(type, "$assertionsDisabled");
      if (field != null && field.isStatic() && field.isSynthetic()) {
        Value value = type.getValue(field);
        if (value instanceof BooleanValue) {
          return ThreeState.fromBoolean(!((BooleanValue)value).value());
        }
      }
    }
    return ThreeState.UNSURE;
  }

  public static String methodKey(Method m) {
    return m.declaringType().name() + '.' + m.name() + m.signature();
  }

  public static String methodNameWithArguments(Method m) {
    return m.name() + "(" + StringUtil.join(m.argumentTypeNames(), StringUtil::getShortName, ", ") + ")";
  }

  public static String methodName(final Method m) {
    return methodName(signatureToName(m.declaringType().signature()), m.name(), m.signature());
  }

  public static boolean methodMatches(@NotNull Method m, @NotNull String name, @NotNull String signature) {
    return name.equals(m.name()) && signature.equals(m.signature());
  }

  public static String methodName(final String className, final String methodName, final String signature) {
    var type = org.jetbrains.org.objectweb.asm.Type.getMethodType(signature);
    var params = Arrays.stream(type.getArgumentTypes())
      .map(org.jetbrains.org.objectweb.asm.Type::getClassName)
      .collect(Collectors.joining(", "));
    return className + "." + methodName + "(" + params + ")";
  }

  public static String signatureToName(String s) {
    return org.jetbrains.org.objectweb.asm.Type.getType(s).getClassName();
  }

  public static String typeNameToSignature(String name) {
    return JNITypeParser.typeNameToSignature(name);
  }

  public static List<Method> declaredMethodsByName(@NotNull ReferenceType type, @NotNull String name) {
    return StreamEx.of(type.methods()).filter(m -> name.equals(m.name())).toList();
  }

  public static @Nullable List<Location> allLineLocations(Method method) {
    try {
      return method.allLineLocations();
    }
    catch (AbsentInformationException ignored) {
      return null;
    }
  }

  public static @Nullable List<Location> allLineLocations(ReferenceType cls) {
    try {
      return DebuggerUtilsAsync.allLineLocationsSync(cls);
    }
    catch (AbsentInformationException ignored) {
      return null;
    }
    catch (ObjectCollectedException ignored) {
      return Collections.emptyList();
    }
  }

  public static int getLineNumber(Location location, boolean zeroBased) {
    try {
      return location.lineNumber() - (zeroBased ? 1 : 0);
    }
    catch (InternalError | IllegalArgumentException e) {
      return -1;
    }
  }

  public static int getCodeIndex(Location location) {
    try {
      return Math.toIntExact(location.codeIndex());
    }
    catch (InternalError | IllegalArgumentException e) {
      return -1;
    }
  }

  public static @Nullable String getSourceName(Location location, @Nullable String defaultName) {
    return getSourceName(location, e -> defaultName);
  }

  public static @Nullable String getSourceName(Location location, @NotNull Function<? super Throwable, String> defaultNameProvider) {
    try {
      return location.sourceName();
    }
    catch (InternalError | AbsentInformationException | IllegalArgumentException e) {
      return defaultNameProvider.apply(e);
    }
  }

  public static boolean isVoid(@NotNull Method method) {
    return "void".equals(method.returnTypeName());
  }

  @Contract("null -> null")
  public static Method getMethod(@Nullable Location location) {
    if (location == null) {
      return null;
    }
    try {
      return location.method();
    }
    catch (IllegalArgumentException e) { // Invalid method id
      LOG.info(e);
    }
    return null;
  }

  public static CompletableFuture<Method> getMethodAsync(LocationImpl location) {
    return location.methodAsync().exceptionally(throwable -> {
      if (DebuggerUtilsAsync.unwrap(throwable) instanceof IllegalArgumentException) { // Invalid method id
        LOG.info(throwable);
        return null;
      }
      throw (RuntimeException)throwable;
    });
  }

  public static @NotNull List<Location> locationsOfLine(@NotNull Method method, int line) {
    try {
      return method.locationsOfLine(DebugProcess.JAVA_STRATUM, null, line);
    }
    catch (AbsentInformationException ignored) {
    }
    return Collections.emptyList();
  }

  public static List<Value> getArgumentValues(@NotNull StackFrame frame) {
    try {
      return frame.getArgumentValues();
    }
    catch (InternalException e) {
      // From Oracle's forums:
      // This could be a JPDA bug. Unexpected JDWP Error: 32 means that an 'opaque' frame was detected at the lower JPDA levels,
      // typically a native frame.
      if (e.errorCode() == JvmtiError.OPAQUE_FRAME /*opaque frame JDI bug*/) {
        return Collections.emptyList();
      }
      else {
        throw e;
      }
    }
  }

  public static Value createValue(VirtualMachineProxyImpl vm, String expectedType, double value) {
    if (PsiTypes.doubleType().getName().equals(expectedType)) {
      return vm.mirrorOf(value);
    }
    if (PsiTypes.floatType().getName().equals(expectedType)) {
      return vm.mirrorOf((float)value);
    }
    if (PsiTypes.longType().getName().equals(expectedType)) {
      return vm.mirrorOf((long)value);
    }
    if (PsiTypes.intType().getName().equals(expectedType)) {
      return vm.mirrorOf((int)value);
    }
    if (PsiTypes.shortType().getName().equals(expectedType)) {
      return vm.mirrorOf((short)value);
    }
    if (PsiTypes.byteType().getName().equals(expectedType)) {
      return vm.mirrorOf((byte)value);
    }
    if (PsiTypes.charType().getName().equals(expectedType)) {
      return vm.mirrorOf((char)value);
    }
    return null;
  }

  public static Value createValue(VirtualMachineProxyImpl vm, String expectedType, long value) {
    if (PsiTypes.longType().getName().equals(expectedType)) {
      return vm.mirrorOf(value);
    }
    if (PsiTypes.intType().getName().equals(expectedType)) {
      return vm.mirrorOf((int)value);
    }
    if (PsiTypes.shortType().getName().equals(expectedType)) {
      return vm.mirrorOf((short)value);
    }
    if (PsiTypes.byteType().getName().equals(expectedType)) {
      return vm.mirrorOf((byte)value);
    }
    if (PsiTypes.charType().getName().equals(expectedType)) {
      return vm.mirrorOf((char)value);
    }
    if (PsiTypes.doubleType().getName().equals(expectedType)) {
      return vm.mirrorOf((double)value);
    }
    if (PsiTypes.floatType().getName().equals(expectedType)) {
      return vm.mirrorOf((float)value);
    }
    return null;
  }

  public static Value createValue(VirtualMachineProxyImpl vm, String expectedType, boolean value) {
    if (PsiTypes.booleanType().getName().equals(expectedType)) {
      return vm.mirrorOf(value);
    }
    return null;
  }

  public static Value createValue(VirtualMachineProxyImpl vm, String expectedType, char value) {
    if (PsiTypes.charType().getName().equals(expectedType)) {
      return vm.mirrorOf(value);
    }
    if (PsiTypes.longType().getName().equals(expectedType)) {
      return vm.mirrorOf((long)value);
    }
    if (PsiTypes.intType().getName().equals(expectedType)) {
      return vm.mirrorOf((int)value);
    }
    if (PsiTypes.shortType().getName().equals(expectedType)) {
      return vm.mirrorOf((short)value);
    }
    if (PsiTypes.byteType().getName().equals(expectedType)) {
      return vm.mirrorOf((byte)value);
    }
    if (PsiTypes.doubleType().getName().equals(expectedType)) {
      return vm.mirrorOf((double)value);
    }
    if (PsiTypes.floatType().getName().equals(expectedType)) {
      return vm.mirrorOf((float)value);
    }
    return null;
  }

  public static String truncateString(final String str) {
    // leave a small gap over XValueNode.MAX_VALUE_LENGTH to detect oversize
    if (str.length() > XValueNode.MAX_VALUE_LENGTH + 5) {
      return str.substring(0, XValueNode.MAX_VALUE_LENGTH + 5);
    }
    return str;
  }

  public static String getThreadStatusText(int statusId) {
    return switch (statusId) {
      case ThreadReference.THREAD_STATUS_MONITOR -> JavaDebuggerBundle.message("status.thread.monitor");
      case ThreadReference.THREAD_STATUS_NOT_STARTED -> JavaDebuggerBundle.message("status.thread.not.started");
      case ThreadReference.THREAD_STATUS_RUNNING -> JavaDebuggerBundle.message("status.thread.running");
      case ThreadReference.THREAD_STATUS_SLEEPING -> JavaDebuggerBundle.message("status.thread.sleeping");
      case ThreadReference.THREAD_STATUS_UNKNOWN -> JavaDebuggerBundle.message("status.thread.unknown");
      case ThreadReference.THREAD_STATUS_WAIT -> JavaDebuggerBundle.message("status.thread.wait");
      case ThreadReference.THREAD_STATUS_ZOMBIE -> JavaDebuggerBundle.message("status.thread.zombie");
      default -> JavaDebuggerBundle.message("status.thread.undefined");
    };
  }

  private static final Key<Map<String, String>> DEBUGGER_ALTERNATIVE_SOURCE_MAPPING = Key.create("DEBUGGER_ALTERNATIVE_SOURCE_MAPPING");

  public static void setAlternativeSourceUrl(String className, String source, Project project) {
    Map<String, String> map = project.getUserData(DEBUGGER_ALTERNATIVE_SOURCE_MAPPING);
    if (map == null) {
      map = new ConcurrentHashMap<>();
      project.putUserData(DEBUGGER_ALTERNATIVE_SOURCE_MAPPING, map);
    }
    map.put(className, source);
  }

  public static @Nullable String getAlternativeSourceUrl(@Nullable String className, Project project) {
    Map<String, String> map = project.getUserData(DEBUGGER_ALTERNATIVE_SOURCE_MAPPING);
    return map != null ? map.get(className) : null;
  }

  public static @Nullable XSourcePosition toXSourcePosition(@Nullable SourcePosition position) {
    if (position != null) {
      VirtualFile file = position.getFile().getVirtualFile();
      if (file == null) {
        file = position.getFile().getOriginalFile().getVirtualFile();
      }
      if (file != null) {
        return new JavaXSourcePosition(position, file);
      }
    }
    return null;
  }

  public static @Nullable SourcePosition toSourcePosition(@Nullable XSourcePosition position, Project project) {
    if (position != null) {
      if (position instanceof JavaXSourcePosition) {
        return ((JavaXSourcePosition)position).mySourcePosition;
      }
      PsiFile psiFile = getPsiFile(position, project);
      if (psiFile != null) {
        return SourcePosition.createFromOffset(psiFile, position.getOffset());
      }
    }
    return null;
  }

  private static class JavaXSourcePosition implements XSourcePosition, ExecutionPointHighlighter.HighlighterProvider {
    private final SourcePosition mySourcePosition;
    private final @NotNull VirtualFile myFile;

    JavaXSourcePosition(@NotNull SourcePosition sourcePosition, @NotNull VirtualFile file) {
      mySourcePosition = sourcePosition;
      myFile = file;
    }

    @Override
    public int getLine() {
      return mySourcePosition.getLine();
    }

    @Override
    public int getOffset() {
      return mySourcePosition.getOffset();
    }

    @Override
    public @NotNull VirtualFile getFile() {
      return myFile;
    }

    @Override
    public @NotNull Navigatable createNavigatable(@NotNull Project project) {
      return XDebuggerUtilImpl.createNavigatable(project, this);
    }

    @Override
    public @Nullable TextRange getHighlightRange() {
      return SourcePositionHighlighter.getHighlightRangeFor(mySourcePosition);
    }
  }

  /**
   * Extract text range suitable for highlighting.
   * <p>
   * The passed text range is cut to fit the line range.
   * Also, whole line highlighting is represented by <code>null</code> return value.
   * @return highlighting range inside the line or null if the whole line should be highlighted
   */
  public static @Nullable TextRange getHighlightingRangeInsideLine(@Nullable TextRange range, @Nullable PsiFile file, int line) {
    if (range != null && file != null) {
      Document document = file.getViewProvider().getDocument();
      if (document != null) {
        TextRange lineRange = DocumentUtil.getLineTextRange(document, line);
        TextRange res = range.intersection(lineRange);
        return lineRange.equals(res) ? null : res;
      }
    }
    return range;
  }

  @RequiresReadLock
  public static @Nullable PsiFile getPsiFile(@Nullable XSourcePosition position, Project project) {
    if (position != null) {
      VirtualFile file = position.getFile();
      if (file.isValid()) {
        return PsiManager.getInstance(project).findFile(file);
      }
    }
    return null;
  }

  /**
   * Decompiler aware version
   */
  public static @Nullable PsiElement findElementAt(@Nullable PsiFile file, int offset) {
    return file != null ? file.findElementAt(offset) : null;
  }

  public static String getLocationMethodQName(@NotNull Location location) {
    StringBuilder res = new StringBuilder();
    ReferenceType type = location.declaringType();
    if (type != null) {
      res.append(type.name()).append('.');
    }
    res.append(getLocationMethodName(location));
    return res.toString();
  }

  public static String getLocationMethodName(@NotNull Location location) {
    if (location instanceof GeneratedLocation generatedLocation) {
      return generatedLocation.methodName();
    }
    Method method = getMethod(location);
    if (method == null) {
      return "<invalid method>";
    }
    return method.name();
  }

  private static PsiElement getNextElement(PsiElement element) {
    PsiElement sibling = element.getNextSibling();
    if (sibling != null) return sibling;
    element = element.getParent();
    if (element != null) return getNextElement(element);
    return null;
  }

  public static boolean isLambdaClassName(String typeName) {
    return getLambdaBaseClassName(typeName) != null;
  }

  public static @Nullable String getLambdaBaseClassName(String typeName) {
    return StringUtil.substringBefore(typeName, "$$Lambda");
  }

  public static boolean isLambdaName(@Nullable String name) {
    return !StringUtil.isEmpty(name) && name.startsWith("lambda$");
  }

  public static boolean isLambda(@Nullable Method method) {
    return method != null && isLambdaName(method.name());
  }

  public static boolean isProxyClassName(@Nullable String name) {
    return !StringUtil.isEmpty(name) && StringUtil.getShortName(name).startsWith("$Proxy");
  }

  public static boolean isProxyClass(@Nullable ReferenceType type) {
    // it may be better to call java.lang.reflect.Proxy#isProxyClass but it is much slower
    return type instanceof ClassType && isProxyClassName(type.name());
  }

  public static final Comparator<Method> LAMBDA_ORDINAL_COMPARATOR = Comparator.comparingInt(m -> getLambdaOrdinal(m.name()));

  public static int getLambdaOrdinal(@NotNull String name) {
    return StringUtil.parseInt(StringUtil.substringAfterLast(name, "$"), -1);
  }

  @RequiresReadLock
  public static List<PsiLambdaExpression> collectLambdas(@NotNull SourcePosition position, final boolean onlyOnTheLine) {
    PsiFile file = position.getFile();
    final int line = position.getLine();
    final Document document = file.getViewProvider().getDocument();
    if (document == null || line < 0 || line >= document.getLineCount()) {
      return Collections.emptyList();
    }
    TextRange lineRange = DocumentUtil.getLineTextRange(document, line);
    // always start from the beginning of the line for consistency
    PsiElement element = file.findElementAt(lineRange.getStartOffset());
    if (element == null) {
      return Collections.emptyList();
    }

    final List<PsiLambdaExpression> lambdas = new SmartList<>();
    final PsiElementVisitor lambdaCollector = new JavaRecursiveElementVisitor() {
      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);
        if (!onlyOnTheLine || getFirstElementOnTheLine(expression, document, line) != null) {
          lambdas.add(expression);
        }
      }
    };
    element.accept(lambdaCollector);
    for (PsiElement sibling = getNextElement(element); sibling != null; sibling = getNextElement(sibling)) {
      if (!intersects(lineRange, sibling)) {
        break;
      }
      sibling.accept(lambdaCollector);
    }
    // add initial lambda if we're inside already
    PsiElement method = getContainingMethod(element);
    if (method instanceof PsiLambdaExpression && !lambdas.contains(method)) {
      lambdas.add((PsiLambdaExpression)method);
    }
    return lambdas;
  }

  public static @Nullable PsiElement getBody(PsiElement method) {
    if (method instanceof PsiParameterListOwner) {
      return ((PsiParameterListOwner)method).getBody();
    }
    else if (method instanceof PsiClassInitializer) {
      return ((PsiClassInitializer)method).getBody();
    }
    return null;
  }

  public static PsiParameter @NotNull [] getParameters(PsiElement method) {
    if (method instanceof PsiParameterListOwner) {
      return ((PsiParameterListOwner)method).getParameterList().getParameters();
    }
    return PsiParameter.EMPTY_ARRAY;
  }

  public static boolean evaluateBoolean(ExpressionEvaluator evaluator, EvaluationContextImpl context) throws EvaluateException {
    Object value = UnBoxingEvaluator.unbox(evaluator.evaluate(context), context);
    if (!(value instanceof BooleanValue)) {
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.boolean.expected"));
    }
    return ((BooleanValue)value).booleanValue();
  }

  public static boolean intersects(@NotNull TextRange range, @NotNull PsiElement elem) {
    TextRange elemRange = elem.getTextRange();
    return elemRange != null && elemRange.intersects(range);
  }

  @RequiresReadLock
  public static @Nullable PsiElement getFirstElementOnTheLine(@NotNull PsiLambdaExpression lambda, Document document, int line) {
    TextRange lineRange = DocumentUtil.getLineTextRange(document, line);
    if (!intersects(lineRange, lambda)) return null;
    PsiElement body = lambda.getBody();
    if (body == null || !intersects(lineRange, body)) return null;

    if (body instanceof PsiCodeBlock) {
      PsiStatement[] statements = ((PsiCodeBlock)body).getStatements();
      if (statements.length == 0) {
        // empty lambda
        LOG.assertTrue(lineRange.contains(body.getTextOffset()));
        return body;
      }
      for (PsiStatement statement : statements) {
        // return first statement starting on the line
        var found = getFirstElementOnTheLine(lineRange, statement);
        if (found != null) {
          return found;
        }
      }
    }
    else {
      // check expression body
      var found = getFirstElementOnTheLine(lineRange, body);
      if (found != null) {
        return found;
      }
    }

    return null;
  }

  private static PsiElement getFirstElementOnTheLine(TextRange lineRange, PsiElement element) {
    if (lineRange.contains(element.getTextOffset())) {
      return element;
    }

    // otherwise check all children
    if (intersects(lineRange, element)) {
      for (PsiElement child : SyntaxTraverser.psiTraverser(element)) {
        if (lineRange.contains(child.getTextOffset())) {
          return child;
        }
      }
    }

    return null;
  }

  public static boolean inTheMethod(@NotNull SourcePosition pos, @NotNull PsiElement method) {
    PsiElement elem = pos.getElementAt();
    if (elem == null) return false;
    return Comparing.equal(getContainingMethod(elem), method);
  }

  public static boolean methodMatches(@NotNull PsiMethod psiMethod,
                                      String className,
                                      @Nullable String name,
                                      String signature,
                                      DebugProcessImpl process) {
    PsiClass containingClass = psiMethod.getContainingClass();
    try {
      if (containingClass != null &&
          (name == null || JVMNameUtil.getJVMMethodName(psiMethod).equals(name)) &&
          JVMNameUtil.getJVMSignature(psiMethod).getName(process).equals(signature)) {
        String methodClassName = JVMNameUtil.getClassVMName(containingClass);
        if (Objects.equals(methodClassName, className)) {
          return true;
        }
        if (methodClassName != null) {
          if (ContainerUtil.exists(process.getVirtualMachineProxy().classesByName(className), t -> instanceOf(t, methodClassName))) {
            return true;
          }
          PsiClass aClass = PositionManagerImpl.findClass(process.getProject(), className, process.getSearchScope(), true);
          return aClass != null && aClass.isInheritor(containingClass, true);
        }
      }
    }
    catch (EvaluateException e) {
      LOG.debug(e);
    }
    return false;
  }

  public static @Nullable PsiElement getContainingMethod(@Nullable PsiElement elem) {
    return PsiTreeUtil.getContextOfType(elem, PsiMethod.class, PsiLambdaExpression.class, PsiClassInitializer.class);
  }

  public static @Nullable PsiElement getContainingMethod(@Nullable SourcePosition position) {
    if (position == null) return null;
    return getContainingMethod(position.getElementAt());
  }

  public static void disableCollection(ObjectReference reference) {
    try {
      reference.disableCollection();
    }
    catch (UnsupportedOperationException ignored) {
      // ignore: some J2ME implementations does not provide this operation
    }
  }

  public static void enableCollection(ObjectReference reference) {
    if (reference instanceof ObjectReferenceImpl) {
      ((ObjectReferenceImpl)reference).enableCollectionAsync();
    }
    else {
      try {
        reference.enableCollection();
      }
      catch (UnsupportedOperationException ignored) {
        // ignore: some J2ME implementations does not provide this operation
      }
    }
  }

  /**
   * Provides mapping from decompiled file line number to the original source code line numbers
   *
   * @param psiFile      decompiled file
   * @param originalLine zero-based decompiled file line number
   * @return zero-based source code line number
   */
  public static int bytecodeToSourceLine(PsiFile psiFile, int originalLine) {
    VirtualFile file = psiFile.getVirtualFile();
    if (file != null) {
      LineNumbersMapping mapping = file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY);
      if (mapping != null) {
        int line = mapping.bytecodeToSource(originalLine + 1);
        if (line > -1) {
          return line - 1;
        }
      }
    }
    return -1;
  }

  public static boolean isInLibraryContent(@Nullable VirtualFile file, @NotNull Project project) {
    return ReadAction.compute(() -> {
      if (file == null) {
        return true;
      }
      else {
        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        return projectFileIndex.isInLibrary(file);
      }
    });
  }

  public static @NotNull Location findOrCreateLocation(@NotNull VirtualMachine virtualMachine, @NotNull StackTraceElement stackTraceElement) {
    return findOrCreateLocation(virtualMachine,
                                stackTraceElement.getClassName(),
                                stackTraceElement.getMethodName(),
                                stackTraceElement.getLineNumber());
  }

  public static @NotNull Location findOrCreateLocation(@NotNull VirtualMachine virtualMachine,
                                                       @NotNull String className,
                                                       @NotNull String methodName,
                                                       int line) {
    ReferenceType classType = ContainerUtil.getFirstItem(virtualMachine.classesByName(className));
    if (classType == null) {
      classType = new GeneratedReferenceType(virtualMachine, className);
    }
    else if (line >= 0) {
      for (Method method : declaredMethodsByName(classType, methodName)) {
        List<Location> locations = locationsOfLine(method, line);
        if (!locations.isEmpty()) {
          return locations.get(0);
        }
      }
    }
    return new GeneratedLocation(classType, methodName, line);
  }
}
