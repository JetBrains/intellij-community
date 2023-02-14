// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class DebuggerUtilsEx
 * @author Jeka
 */
package com.intellij.debugger.impl;

import com.intellij.application.options.CodeStyle;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.UnBoxingEvaluator;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.jdi.GeneratedLocation;
import com.intellij.debugger.jdi.JvmtiError;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.memory.ui.CollectionHistoryView;
import com.intellij.debugger.requests.Requestor;
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
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.content.Content;
import com.intellij.unscramble.ThreadDumpPanel;
import com.intellij.unscramble.ThreadState;
import com.intellij.util.DocumentUtil;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
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
import com.jetbrains.jdi.LocationImpl;
import com.jetbrains.jdi.ObjectReferenceImpl;
import com.sun.jdi.*;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import one.util.streamex.StreamEx;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.PatternSyntaxException;

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


  public static boolean isAssignableFrom(@NotNull String baseQualifiedName, @NotNull Type checkedType) {
    if (checkedType instanceof ReferenceType) {
      if (CommonClassNames.JAVA_LANG_OBJECT.equals(baseQualifiedName)) {
        return true;
      }
      return getSuperClass(baseQualifiedName, (ReferenceType)checkedType) != null;
    }
    return baseQualifiedName.equals(checkedType.name());
  }

  public static ReferenceType getSuperClass(@NotNull final String baseQualifiedName, @NotNull ReferenceType checkedType) {
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

  public static boolean valuesEqual(Value val1, Value val2) {
    if (val1 == null) {
      return val2 == null;
    }
    if (val2 == null) {
      return false;
    }
    if (val1 instanceof StringReference && val2 instanceof StringReference) {
      return ((StringReference)val1).value().equals(((StringReference)val2).value());
    }
    return val1.equals(val2);
  }

  public static ClassFilter create(Element element) throws InvalidDataException {
    ClassFilter filter = new ClassFilter();
    DefaultJDOMExternalizer.readExternal(filter, element);
    return filter;
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

  public static ClassFilter[] readFilters(List<? extends Element> children) {
    if (ContainerUtil.isEmpty(children)) {
      return ClassFilter.EMPTY_ARRAY;
    }

    //do not leave null elements in the resulting array in case of read errors
    List<ClassFilter> filters = new ArrayList<>(children.size());
    for (Element child : children) {
      try {
        filters.add(create(child));
      }
      catch (InvalidDataException e) {
        LOG.error(e);
      }
    }
    return filters.toArray(ClassFilter.EMPTY_ARRAY);
  }

  public static void writeFilters(@NotNull Element parentNode,
                                  @NonNls String tagName,
                                  ClassFilter[] filters) throws WriteExternalException {
    for (ClassFilter filter : filters) {
      Element element = new Element(tagName);
      parentNode.addContent(element);
      DefaultJDOMExternalizer.writeExternal(filter, element);
    }
  }

  public static boolean filterEquals(ClassFilter[] filters1, ClassFilter[] filters2) {
    if (filters1.length != filters2.length) {
      return false;
    }
    final Set<ClassFilter> f1 = new HashSet<>(Math.max((int)(filters1.length / .75f) + 1, 16));
    final Set<ClassFilter> f2 = new HashSet<>(Math.max((int)(filters2.length / .75f) + 1, 16));
    Collections.addAll(f1, filters1);
    Collections.addAll(f2, filters2);
    return f2.equals(f1);
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

  @NotNull
  public static List<Pair<Breakpoint, Event>> getEventDescriptors(@Nullable SuspendContextImpl suspendContext) {
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

  public static void addThreadDump(Project project, List<ThreadState> threads, RunnerLayoutUi ui, GlobalSearchScope searchScope) {
    final TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    consoleBuilder.filters(ExceptionFilters.getFilters(searchScope));
    final ConsoleView consoleView = consoleBuilder.getConsole();
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    consoleView.allowHeavyFilters();
    final ThreadDumpPanel panel = new ThreadDumpPanel(project, consoleView, toolbarActions, threads);

    String id = JavaDebuggerBundle.message("thread.dump.name", DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis()));
    final Content content = ui.createContent(id + " " + myThreadDumpsCount, panel, id, null, null);
    content.putUserData(RunnerContentUi.LIGHTWEIGHT_CONTENT_MARKER, Boolean.TRUE);
    content.setCloseable(true);
    content.setDescription(JavaDebuggerBundle.message("thread.dump"));
    ui.addContent(content);
    ui.selectAndFocus(content, true, true);
    myThreadDumpsCount++;
    Disposer.register(content, consoleView);
    ui.selectAndFocus(content, true, false);
    if (threads.size() > 0) {
      panel.selectStackFrame(0);
    }
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

  public static StringReference mirrorOfString(@NotNull String s, VirtualMachineProxyImpl virtualMachineProxy, EvaluationContext context)
    throws EvaluateException {
    return context.computeAndKeep(() -> virtualMachineProxy.mirrorOf(s));
  }

  public static ArrayReference mirrorOfArray(@NotNull ArrayType arrayType, int dimension, EvaluationContext context)
    throws EvaluateException {
    return context.computeAndKeep(() -> context.getDebugProcess().newInstance(arrayType, dimension));
  }

  public static void setValuesNoCheck(ArrayReference array, List<Value> values) throws ClassNotLoadedException, InvalidTypeException {
    if (array instanceof ArrayReferenceImpl) {
      ((ArrayReferenceImpl)array).setValues(0, values, 0, -1, false);
    }
    else {
      array.setValues(values);
    }
  }

  @NotNull
  public static CodeFragmentFactory getCodeFragmentFactory(@Nullable PsiElement context, @Nullable FileType fileType) {
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

  @NotNull
  public static CodeFragmentFactory findAppropriateCodeFragmentFactory(final TextWithImports text, final PsiElement context) {
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
  @NotNull
  public static ThreeState getEffectiveAssertionStatus(@NotNull Location location) {
    ReferenceType type = location.declaringType();
    if (type instanceof ClassType) {
      Field field = type.fieldByName("$assertionsDisabled");
      if (field != null && field.isStatic() && field.isSynthetic()) {
        Value value = type.getValue(field);
        if (value instanceof BooleanValue) {
          return ThreeState.fromBoolean(!((BooleanValue)value).value());
        }
      }
    }
    return ThreeState.UNSURE;
  }

  private static class SigReader {
    final String buffer;
    int pos = 0;

    SigReader(String s) {
      buffer = s;
    }

    int get() {
      return buffer.charAt(pos++);
    }

    int peek() {
      return buffer.charAt(pos);
    }

    boolean eof() {
      return buffer.length() <= pos;
    }

    @NonNls
    String getSignature() {
      if (eof()) return "";

      switch (get()) {
        case 'Z' -> {
          return "boolean";
        }
        case 'B' -> {
          return "byte";
        }
        case 'C' -> {
          return "char";
        }
        case 'S' -> {
          return "short";
        }
        case 'I' -> {
          return "int";
        }
        case 'J' -> {
          return "long";
        }
        case 'F' -> {
          return "float";
        }
        case 'D' -> {
          return "double";
        }
        case 'V' -> {
          return "void";
        }
        case 'L' -> {
          int start = pos;
          pos = buffer.indexOf(';', start) + 1;
          LOG.assertTrue(pos > 0);
          return buffer.substring(start, pos - 1).replace('/', '.');
        }
        case '[' -> {
          return getSignature() + "[]";
        }
        case '(' -> {
          StringBuilder result = new StringBuilder("(");
          String separator = "";
          while (peek() != ')') {
            result.append(separator);
            result.append(getSignature());
            separator = ", ";
          }
          get();
          result.append(")");
          return getSignature() + " " + getClassName() + "." + getMethodName() + " " + result;
        }
        default -> {
          //          LOG.assertTrue(false, "unknown signature " + buffer);
          return null;
        }
      }
    }

    String getMethodName() {
      return "";
    }

    String getClassName() {
      return "";
    }
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
    try {
      return new SigReader(signature) {
        @Override
        String getMethodName() {
          return methodName;
        }

        @Override
        String getClassName() {
          return className;
        }
      }.getSignature();
    }
    catch (Exception ignored) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Internal error : unknown signature" + signature);
      }
      return className + "." + methodName;
    }
  }

  public static String signatureToName(String s) {
    return new SigReader(s).getSignature();
  }

  public static List<Method> declaredMethodsByName(@NotNull ReferenceType type, @NotNull String name) {
    return StreamEx.of(type.methods()).filter(m -> name.equals(m.name())).toList();
  }

  @Nullable
  public static List<Location> allLineLocations(Method method) {
    try {
      return method.allLineLocations();
    }
    catch (AbsentInformationException ignored) {
      return null;
    }
  }

  @Nullable
  public static List<Location> allLineLocations(ReferenceType cls) {
    try {
      return cls.allLineLocations();
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

  public static String getSourceName(Location location, Function<? super Throwable, String> defaultName) {
    try {
      return location.sourceName();
    }
    catch (InternalError | AbsentInformationException | IllegalArgumentException e) {
      return defaultName.apply(e);
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

  @NotNull
  public static List<Location> locationsOfLine(@NotNull Method method, int line) {
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

  public static String prepareValueText(String text, Project project) {
    text = StringUtil.unquoteString(text);
    text = StringUtil.unescapeStringCharacters(text);
    int tabSize = CodeStyle.getSettings(project).getTabSize(JavaFileType.INSTANCE);
    if (tabSize < 0) {
      tabSize = 0;
    }
    return text.replace("\t", StringUtil.repeat(" ", tabSize));
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

  @Nullable
  public static String getAlternativeSourceUrl(@Nullable String className, Project project) {
    Map<String, String> map = project.getUserData(DEBUGGER_ALTERNATIVE_SOURCE_MAPPING);
    return map != null ? map.get(className) : null;
  }

  @Nullable
  public static XSourcePosition toXSourcePosition(@Nullable SourcePosition position) {
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

  @Nullable
  public static SourcePosition toSourcePosition(@Nullable XSourcePosition position, Project project) {
    if (position != null) {
      if (position instanceof JavaXSourcePosition) {
        return ((JavaXSourcePosition)position).mySourcePosition;
      }
      PsiFile psiFile = getPsiFile(position, project);
      if (psiFile != null) {
        return SourcePosition.createFromLine(psiFile, position.getLine());
      }
    }
    return null;
  }

  private static class JavaXSourcePosition implements XSourcePosition, ExecutionPointHighlighter.HighlighterProvider {
    private final SourcePosition mySourcePosition;
    @NotNull private final VirtualFile myFile;

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

    @NotNull
    @Override
    public VirtualFile getFile() {
      return myFile;
    }

    @NotNull
    @Override
    public Navigatable createNavigatable(@NotNull Project project) {
      return XDebuggerUtilImpl.createNavigatable(project, this);
    }

    @Nullable
    @Override
    public TextRange getHighlightRange() {
      TextRange range = SourcePositionHighlighter.getHighlightRangeFor(mySourcePosition);
      PsiFile file = mySourcePosition.getFile();
      if (range != null) {
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document != null) {
          TextRange lineRange = DocumentUtil.getLineTextRange(document, getLine());
          TextRange res = range.intersection(lineRange);
          return lineRange.equals(res) ? null : res; // highlight the whole line for multiline lambdas
        }
      }
      return range;
    }
  }

  @Nullable
  public static TextRange intersectWithLine(@Nullable TextRange range, @Nullable PsiFile file, int line) {
    if (range != null && file != null) {
      Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      if (document != null) {
        range = range.intersection(DocumentUtil.getLineTextRange(document, line));
      }
    }
    return range;
  }

  @Nullable
  public static PsiFile getPsiFile(@Nullable XSourcePosition position, Project project) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
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
  @Nullable
  public static PsiElement findElementAt(@Nullable PsiFile file, int offset) {
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
    return location instanceof GeneratedLocation ? ((GeneratedLocation)location).methodName() : location.method().name();
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

  @Nullable
  public static String getLambdaBaseClassName(String typeName) {
    return StringUtil.substringBefore(typeName, "$$Lambda$");
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

  public static List<PsiLambdaExpression> collectLambdas(@NotNull SourcePosition position, final boolean onlyOnTheLine) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    PsiFile file = position.getFile();
    final int line = position.getLine();
    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
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

  @Nullable
  public static PsiElement getBody(PsiElement method) {
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

  @Nullable
  public static PsiElement getFirstElementOnTheLine(PsiLambdaExpression lambda, Document document, int line) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    TextRange lineRange = DocumentUtil.getLineTextRange(document, line);
    if (!intersects(lineRange, lambda)) return null;
    PsiElement body = lambda.getBody();
    if (body == null || !intersects(lineRange, body)) return null;
    if (body instanceof PsiCodeBlock) {
      PsiStatement[] statements = ((PsiCodeBlock)body).getStatements();
      if (statements.length > 0) {
        for (PsiStatement statement : statements) {
          // return first statement starting on the line
          if (lineRange.contains(statement.getTextOffset())) {
            return statement;
          }
          // otherwise check all children
          else if (intersects(lineRange, statement)) {
            for (PsiElement element : SyntaxTraverser.psiTraverser(statement)) {
              if (lineRange.contains(element.getTextOffset())) {
                return element;
              }
            }
          }
        }
        return null;
      }
    }
    return body;
  }

  public static boolean inTheMethod(@NotNull SourcePosition pos, @NotNull PsiElement method) {
    PsiElement elem = pos.getElementAt();
    if (elem == null) return false;
    return Comparing.equal(getContainingMethod(elem), method);
  }

  public static boolean methodMatches(@NotNull PsiMethod psiMethod,
                                      String className,
                                      String name,
                                      String signature,
                                      DebugProcessImpl process) {
    PsiClass containingClass = psiMethod.getContainingClass();
    try {
      if (containingClass != null &&
          JVMNameUtil.getJVMMethodName(psiMethod).equals(name) &&
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

  @Nullable
  public static PsiElement getContainingMethod(@Nullable PsiElement elem) {
    return PsiTreeUtil.getContextOfType(elem, PsiMethod.class, PsiLambdaExpression.class, PsiClassInitializer.class);
  }

  @Nullable
  public static PsiElement getContainingMethod(@Nullable SourcePosition position) {
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
}
