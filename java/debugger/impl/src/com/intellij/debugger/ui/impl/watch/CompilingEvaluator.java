/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.Modifier;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.sun.jdi.*;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.*;

/**
* @author egor
*/
class CompilingEvaluator implements ExpressionEvaluator {
  private final TextWithImports myText;

  public CompilingEvaluator(TextWithImports text) {
    myText = text;
  }

  @Override
  public Value getValue() {
    return null;
  }

  @Override
  public Modifier getModifier() {
    return null;
  }

  @Override
  public Value evaluate(EvaluationContext context) throws EvaluateException {
    try {
      DebugProcess process = context.getDebugProcess();
      ThreadReference threadReference = context.getSuspendContext().getThread().getThreadReference();

      ClassLoaderReference classLoader = getClassLoader(context);

      Collection<OutputFileObject> classes = compile();

      ClassType mainClass = defineClasses(classes, context, process, threadReference, classLoader);

      Method foo = mainClass.methodsByName(GEN_METHOD_NAME).get(0);
      return mainClass.invokeMethod(threadReference, foo, Collections.<Value>emptyList() ,ClassType.INVOKE_SINGLE_THREADED);
    }
    catch (Exception e) {
      throw new EvaluateException(e.getMessage());
    }
  }

  private static ClassLoaderReference getClassLoader(EvaluationContext context)
    throws EvaluateException, InvocationException, InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException {
    // TODO: cache
    DebugProcess process = context.getDebugProcess();
    ClassType loaderClass = (ClassType)process.findClass(context, "java.net.URLClassLoader", context.getClassLoader());
    Method ctorMethod = loaderClass.concreteMethodByName("<init>", "([Ljava/net/URL;)V");
    ThreadReference threadReference = context.getSuspendContext().getThread().getThreadReference();
    return (ClassLoaderReference)loaderClass.newInstance(threadReference, ctorMethod,
                                                         Arrays.asList(createURLArray(context)), ClassType.INVOKE_SINGLE_THREADED);
  }

  private static ClassType defineClasses(Collection<OutputFileObject> classes,
                                         EvaluationContext context,
                                         DebugProcess process,
                                         ThreadReference threadReference,
                                         ClassLoaderReference classLoader)
    throws EvaluateException, InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException, InvocationException {

    VirtualMachineProxyImpl proxy = (VirtualMachineProxyImpl)process.getVirtualMachineProxy();
    for (OutputFileObject cls : classes) {
      Method defineMethod = ((ClassType)classLoader.referenceType()).concreteMethodByName("defineClass", "(Ljava/lang/String;[BII)Ljava/lang/Class;");
      byte[] bytes = cls.toByteArray();
      ArrayList<Value> args = new ArrayList<Value>();
      args.add(proxy.mirrorOf(cls.myOrigName));
      args.add(mirrorOf(bytes, context, process));
      args.add(proxy.mirrorOf(0));
      args.add(proxy.mirrorOf(bytes.length));
      classLoader.invokeMethod(threadReference, defineMethod, args, ClassType.INVOKE_SINGLE_THREADED);
    }
    return (ClassType)process.findClass(context, GEN_CLASS_FULL_NAME, classLoader);
  }

  private static ArrayReference mirrorOf(byte[] bytes, EvaluationContext context, DebugProcess process)
    throws EvaluateException, InvalidTypeException, ClassNotLoadedException {
    ArrayType arrayClass = (ArrayType)process.findClass(context, "byte[]", context.getClassLoader());
    ArrayReference reference = process.newInstance(arrayClass, bytes.length);
    reference.disableCollection();
    for (int i = 0; i < bytes.length; i++) {
      reference.setValue(i, ((VirtualMachineProxyImpl)process.getVirtualMachineProxy()).mirrorOf(bytes[i]));
    }
    return reference;
  }

  private static final String GEN_CLASS_NAME = "Evaluator";
  private static final String GEN_CLASS_PACKAGE = "dummy";
  private static final String GEN_CLASS_FULL_NAME = GEN_CLASS_PACKAGE + '.' + GEN_CLASS_NAME;
  private static final String GEN_METHOD_NAME = "eval";

  private static String createClassCode(TextWithImports body) {
    StringBuilder text = new StringBuilder();
    text.append("package " + GEN_CLASS_PACKAGE + ";");
    String imports = body.getImports();
    if (!imports.isEmpty()) {
      for (String s : imports.split(",")) {
        text.append("import " + s + ";");
      }
    }
    String bodyText = body.getText();
    if (!bodyText.endsWith(";")) {
      bodyText += ';';
    }
    text.append("public class " + GEN_CLASS_NAME + " { public static Object " + GEN_METHOD_NAME + "() throws Exception {" + bodyText + "}}");
    return text.toString();
  }

  private static ArrayReference createURLArray(EvaluationContext context)
    throws EvaluateException, InvocationException, InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException {
    DebugProcess process = context.getDebugProcess();
    ArrayType arrayType = (ArrayType)process.findClass(context, "java.net.URL[]", context.getClassLoader());
    ArrayReference arrayRef = arrayType.newInstance(1);
    ClassType classType = (ClassType)process.findClass(context, "java.net.URL", context.getClassLoader());
    VirtualMachineProxyImpl proxy = (VirtualMachineProxyImpl)process.getVirtualMachineProxy();
    ThreadReference threadReference = context.getSuspendContext().getThread().getThreadReference();
    ObjectReference reference = classType.newInstance(threadReference, classType.concreteMethodByName("<init>", "(Ljava/lang/String;)V"),
                                                      Arrays.asList(proxy.mirrorOf("file:a")), ClassType.INVOKE_SINGLE_THREADED);
    arrayRef.setValues(Arrays.asList(reference));
    return arrayRef;
  }

  ///////////////// Compiler stuff

  private Collection<OutputFileObject> compile() throws EvaluateException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    MemoryFileManager manager = new MemoryFileManager(compiler);
    DiagnosticCollector<JavaFileObject> diagnostic = new DiagnosticCollector<JavaFileObject>();
    if (!compiler.getTask(null, manager, diagnostic, null, null, Arrays
      .asList(new SourceFileObject(GEN_CLASS_NAME, JavaFileObject.Kind.SOURCE, createClassCode(myText)))).call()) {
      // TODO: show only errors
      throw new EvaluateException(diagnostic.getDiagnostics().get(0).getMessage(Locale.getDefault()));
    }
    return manager.classes;
  }

  private static URI getUri(String name, JavaFileObject.Kind kind) {
    return URI.create("memo:///" + name.replace('.', '/') + kind.extension);
  }

  private static class SourceFileObject extends SimpleJavaFileObject {
    private final String myContent;

    SourceFileObject(String name, Kind kind, String content) {
      super(getUri(name, kind), kind);
      myContent = content;
    }

    @Override
    public CharSequence getCharContent(boolean ignore) {
      return myContent;
    }
  }

  private static class OutputFileObject extends SimpleJavaFileObject {
    private final ByteArrayOutputStream myStream = new ByteArrayOutputStream();
    private final String myOrigName;

    OutputFileObject(String name, Kind kind) {
      super(getUri(name, kind), kind);
      myOrigName = name;
    }

    byte[] toByteArray() {
      return myStream.toByteArray();
    }

    @Override
    public ByteArrayOutputStream openOutputStream() {
      return myStream;
    }
  }

  private static class MemoryFileManager extends ForwardingJavaFileManager {
    private final Collection<OutputFileObject> classes = new ArrayList<OutputFileObject>();

    MemoryFileManager(JavaCompiler compiler) {
      super(compiler.getStandardFileManager(null, null, null));
    }

    @Override
    public OutputFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject source) {
      OutputFileObject mc = new OutputFileObject(name, kind);
      classes.add(mc);
      return mc;
    }
  }

}
