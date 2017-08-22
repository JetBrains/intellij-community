/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.Modifier;
import com.intellij.debugger.impl.ClassLoadingUtils;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.ClassObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ClassType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.util.Collection;

/**
* @author egor
*/
public abstract class CompilingEvaluator implements ExpressionEvaluator {
  @NotNull protected final Project myProject;
  @NotNull protected final PsiElement myPsiContext;
  @NotNull protected final ExtractLightMethodObjectHandler.ExtractedData myData;

  public CompilingEvaluator(@NotNull Project project, @NotNull PsiElement context, @NotNull ExtractLightMethodObjectHandler.ExtractedData data) {
    myProject = project;
    myPsiContext = context;
    myData = data;
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
    DebugProcess process = evaluationContext.getDebugProcess();

    EvaluationContextImpl autoLoadContext = ((EvaluationContextImpl)evaluationContext).createEvaluationContext(evaluationContext.computeThisObject());
    autoLoadContext.setAutoLoadClasses(true);

    ClassLoaderReference classLoader = ClassLoadingUtils.getClassLoader(autoLoadContext, process);
    autoLoadContext.setClassLoader(classLoader);

    String version = ((VirtualMachineProxyImpl)process.getVirtualMachineProxy()).version();
    Collection<ClassObject> classes = compile(JavaSdkVersion.fromVersionString(version));

    defineClasses(classes, autoLoadContext, process, classLoader);

    try {
      // invoke base evaluator on call code
      SourcePosition position = ContextUtil.getSourcePosition(evaluationContext);
      ExpressionEvaluator evaluator =
        DebuggerInvocationUtil.commitAndRunReadAction(myProject, new EvaluatingComputable<ExpressionEvaluator>() {
          @Override
          public ExpressionEvaluator compute() throws EvaluateException {
            TextWithImports callCode = getCallCode();
            PsiElement copyContext = myData.getAnchor();
            CodeFragmentFactory factory = DebuggerUtilsEx.findAppropriateCodeFragmentFactory(callCode, copyContext);
            return factory.getEvaluatorBuilder().build(factory.createCodeFragment(callCode, copyContext, myProject), position);
          }
        });
      return evaluator.evaluate(autoLoadContext);
    }
    catch (Exception e) {
      throw new EvaluateException("Error during generated code invocation " + e, e);
    }
  }

  private ClassType defineClasses(Collection<ClassObject> classes,
                                  EvaluationContext context,
                                  DebugProcess process,
                                  ClassLoaderReference classLoader) throws EvaluateException {
    JavaSdkVersion targetVersion = JavaSdkVersion.fromVersionString(((VirtualMachineProxyImpl)process.getVirtualMachineProxy()).version());
    boolean useMagicAccessorImpl = targetVersion != null && !targetVersion.isAtLeast(JavaSdkVersion.JDK_1_9);

    for (ClassObject cls : classes) {
      if (cls.getPath().contains(GEN_CLASS_NAME)) {
        byte[] bytes = cls.getContent();
        if (bytes != null) {
          if (useMagicAccessorImpl) {
            bytes = changeSuperToMagicAccessor(bytes);
          }
          ClassLoadingUtils.defineClass(cls.getClassName(), bytes, context, process, classLoader);
        }
      }
    }
    return (ClassType)process.findClass(context, getGenClassQName(), classLoader);
  }

  private static byte[] changeSuperToMagicAccessor(byte[] bytes) {
    ClassWriter classWriter = new ClassWriter(0);
    ClassVisitor classVisitor = new ClassVisitor(Opcodes.API_VERSION, classWriter) {
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

  public static String getGeneratedClassName() {
    return GEN_CLASS_NAME;
  }

  private static final String GEN_CLASS_NAME = "GeneratedEvaluationClass";
  //private static final String GEN_CLASS_PACKAGE = "dummy";
  //private static final String GEN_CLASS_FULL_NAME = GEN_CLASS_PACKAGE + '.' + GEN_CLASS_NAME;
  //private static final String GEN_METHOD_NAME = "invoke";


  protected String getGenClassQName() {
    return ReadAction.compute(() -> JVMNameUtil.getNonAnonymousClassName(myData.getGeneratedInnerClass()));
  }

  ///////////////// Compiler stuff

  @NotNull
  protected abstract Collection<ClassObject> compile(@Nullable JavaSdkVersion debuggeeVersion) throws EvaluateException;

}
