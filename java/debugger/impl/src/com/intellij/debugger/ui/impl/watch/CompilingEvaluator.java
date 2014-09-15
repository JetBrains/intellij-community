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

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.EvaluatingComputable;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.Modifier;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.Opcodes;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
* @author egor
*/
public class CompilingEvaluator implements ExpressionEvaluator {
  private final TextWithImports myText;
  private final PsiCodeFragment myCodeFragment;
  private final PsiElement myPsiContext;
  @NotNull private final ExtractLightMethodObjectHandler.ExtractedData myData;
  private final EvaluationDescriptor myDescriptor;

  public CompilingEvaluator(TextWithImports text,
                            PsiCodeFragment codeFragment,
                            PsiElement context,
                            @NotNull ExtractLightMethodObjectHandler.ExtractedData data,
                            EvaluationDescriptor descriptor) {
    myText = text;
    myCodeFragment = codeFragment;
    myPsiContext = context;
    myData = data;
    myDescriptor = descriptor;
  }

  @Override
  public Value getValue() {
    return null;
  }

  @Override
  public Modifier getModifier() {
    return null;
  }

  private TextWithImports getCallCode() {
    return new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, myData.getGeneratedCallText());
  }

  @Override
  public Value evaluate(final EvaluationContext evaluationContext) throws EvaluateException {
    try {
      DebugProcess process = evaluationContext.getDebugProcess();
      ThreadReference threadReference = evaluationContext.getSuspendContext().getThread().getThreadReference();

      ClassLoaderReference classLoader = getClassLoader(evaluationContext);

      Collection<OutputFileObject> classes = compile();

      ClassType mainClass = defineClasses(classes, evaluationContext, process, threadReference, classLoader);

      //Method foo = mainClass.methodsByName(GEN_METHOD_NAME).get(0);
      //return mainClass.invokeMethod(threadReference, foo, Collections.<Value>emptyList() ,ClassType.INVOKE_SINGLE_THREADED);

      // invoke base evaluator on call code
      final Project project = myPsiContext.getProject();
      ExpressionEvaluator evaluator =
        DebuggerInvocationUtil.commitAndRunReadAction(project, new EvaluatingComputable<ExpressionEvaluator>() {
          @Override
          public ExpressionEvaluator compute() throws EvaluateException {
            final TextWithImports callCode = getCallCode();
            PsiElement copyContext = myData.getAnchor();
            final CodeFragmentFactory factory = DebuggerUtilsEx.findAppropriateCodeFragmentFactory(callCode, copyContext);
            return factory.getEvaluatorBuilder().
              build(factory.createCodeFragment(callCode, copyContext, project),
                    ContextUtil.getSourcePosition(evaluationContext));
          }
        });
      ((EvaluationContextImpl)evaluationContext).setClassLoader(classLoader);
      return evaluator.evaluate(evaluationContext);
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
    Method ctorMethod = loaderClass.concreteMethodByName("<init>", "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    ThreadReference threadReference = context.getSuspendContext().getThread().getThreadReference();
    return (ClassLoaderReference)loaderClass.newInstance(threadReference, ctorMethod,
                                                         Arrays.asList(createURLArray(context), context.getClassLoader()), ClassType.INVOKE_SINGLE_THREADED);
  }

  private ClassType defineClasses(Collection<OutputFileObject> classes,
                                         EvaluationContext context,
                                         DebugProcess process,
                                         ThreadReference threadReference,
                                         ClassLoaderReference classLoader)
    throws EvaluateException, InvalidTypeException, ClassNotLoadedException, IncompatibleThreadStateException, InvocationException {

    VirtualMachineProxyImpl proxy = (VirtualMachineProxyImpl)process.getVirtualMachineProxy();
    for (OutputFileObject cls : classes) {
      if (cls.getName().contains(getGenClassName())) {
        Method defineMethod =
          ((ClassType)classLoader.referenceType()).concreteMethodByName("defineClass", "(Ljava/lang/String;[BII)Ljava/lang/Class;");
        byte[] bytes = changeSuperToMagicAccessor(cls.toByteArray());
        ArrayList<Value> args = new ArrayList<Value>();
        args.add(proxy.mirrorOf(cls.myOrigName));
        args.add(mirrorOf(bytes, context, process));
        args.add(proxy.mirrorOf(0));
        args.add(proxy.mirrorOf(bytes.length));
        classLoader.invokeMethod(threadReference, defineMethod, args, ClassType.INVOKE_SINGLE_THREADED);
      }
    }
    return (ClassType)process.findClass(context, getGenClassFullName(), classLoader);
  }

  private static byte[] changeSuperToMagicAccessor(byte[] bytes) {
    ClassWriter classWriter = new ClassWriter(0);
    ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM5, classWriter) {
      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if ("java/lang/Object".equals(superName)) {
          superName = "sun/reflect/MagicAccessorImpl";
        }
        super.visit(version, access, name, signature, superName, interfaces);
      }
    };
    new ClassReader(bytes).accept(classVisitor, 0);
    return classWriter.toByteArray();
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

  public static String getGeneratedClassName() {
    return GEN_CLASS_NAME;
  }

  private static final String GEN_CLASS_NAME = "GeneratedEvaluationClass";
  private static final String GEN_CLASS_PACKAGE = "dummy";
  private static final String GEN_CLASS_FULL_NAME = GEN_CLASS_PACKAGE + '.' + GEN_CLASS_NAME;
  private static final String GEN_METHOD_NAME = "invoke";

  private String getClassCode() {
    if (myData != null) {
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Override
        public String compute() {
          //String text = myData.getGeneratedInnerClass().getText();
          ////TODO: remove
          //String prefix = "public static";
          //if (text.startsWith(prefix)) {
          //  text = "public" + text.substring(prefix.length());
          //}
          //PsiElement[] children = ((PsiJavaFile)myPsiContext.getContainingFile()).getImportList().getChildren();
          //StringBuilder imports = new StringBuilder();
          //for (PsiElement child : children) {
          //  if (child instanceof PsiImportStatement) {
          //    String name = ((PsiImportStatement)child).getImportReference().getQualifiedName();
          //    imports.append("import ").append(name).append(";");
          //  }
          //}
          //text = text.replace("class " + GEN_CLASS_NAME, "class " + getGenClassName());
          //text = text.replace(GEN_CLASS_NAME + "(", getGenClassName() + "(");
          //text = text.replace(((PsiClass)myData.getGeneratedInnerClass().getParent()).getName() + "." + GEN_CLASS_NAME, getGenClassName());
          //return "package " + getGenPackageName() + "; " + imports.toString() + text;
          return myData.getGeneratedInnerClass().getContainingFile().getText();
        }
      });
    }
    return null;
  }

  private String getGenPackageName() {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return ((PsiJavaFile)myData.getGeneratedInnerClass().getContainingFile()).getPackageName();
      }
    });
  }

  private String getMainClassName() {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return ((PsiClass)myData.getGeneratedInnerClass().getParent()).getName();
      }
    });
  }

  private String getGenClassName() {
    return getMainClassName() + '$' + GEN_CLASS_NAME;
  }

  private String getGenClassFullName() {
    String packageName = getGenPackageName();
    if (packageName.isEmpty()) {
      return getGenClassName();
    }
    return packageName + '.' + getGenClassName();
  }

  //private String createClassCode() {
  //  return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
  //    @Override
  //    public String compute() {
  //      try {
  //        myExtractedData =
  //          ExtractLightMethodObjectHandler.extractLightMethodObject(myCodeFragment.getProject(), myFile , myCodeFragment, "test");
  //      }
  //      catch (PrepareFailedException e) {
  //        e.printStackTrace();
  //      }
  //      return null;
  //    }
  //  });
  //}

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
      .asList(new SourceFileObject(getMainClassName(), JavaFileObject.Kind.SOURCE, getClassCode()))).call()) {
      // TODO: show only errors
      throw new EvaluateException(diagnostic.getDiagnostics().get(0).toString());
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
