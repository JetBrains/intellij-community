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

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: lex
 * Date: Sep 2, 2003
 * Time: 11:25:59 AM
 */
public class JVMNameUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.JVMNameUtil");

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String getPrimitiveSignature(String typeName) {
    if(PsiType.BOOLEAN.getCanonicalText().equals(typeName)) {
      return "Z";
    }
    else if (PsiType.BYTE.getCanonicalText().equals(typeName)) {
      return "B";
    }
    else if (PsiType.CHAR.getCanonicalText().equals(typeName)) {
      return "C";
    }
    else if (PsiType.SHORT.getCanonicalText().equals(typeName)) {
      return "S";
    }
    else if (PsiType.INT.getCanonicalText().equals(typeName)) {
      return "I";
    }
    else if (PsiType.LONG.getCanonicalText().equals(typeName)) {
      return "J";
    }
    else if (PsiType.FLOAT.getCanonicalText().equals(typeName)) {
      return "F";
    }
    else if (PsiType.DOUBLE.getCanonicalText().equals(typeName)) {
      return "D";
    }
    else if (PsiType.VOID.getCanonicalText().equals(typeName)) {
      return "V";
    }
    return null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void appendJVMSignature(JVMNameBuffer buffer , PsiType type){
    if (type == null) {
      return;
    }
    final PsiType psiType = TypeConversionUtil.erasure(type);
    if (psiType instanceof PsiArrayType) {
      buffer.append(new JVMRawText("["));
      appendJVMSignature(buffer, ((PsiArrayType) psiType).getComponentType());
    }
    else if (psiType instanceof PsiClassType) {
      final JVMName jvmName = getJVMQualifiedName(psiType);
      appendJvmClassQualifiedName(buffer, jvmName);
    }
    else if (psiType instanceof PsiPrimitiveType) {
      buffer.append(getPrimitiveSignature(psiType.getCanonicalText()));
    }
    else {
      LOG.error("unknown type " + type.getCanonicalText());
    }
  }

  private static void appendJvmClassQualifiedName(JVMNameBuffer buffer, final JVMName jvmName) {
    buffer.append("L");
    if(jvmName instanceof JVMRawText) {
      buffer.append(((JVMRawText)jvmName).getName().replace('.','/'));
    }
    else {
      buffer.append(new JVMName() {
        public String getName(DebugProcessImpl process) throws EvaluateException {
          return jvmName.getName(process).replace('.','/');
        }

        public String getDisplayName(DebugProcessImpl debugProcess) {
          return jvmName.getDisplayName(debugProcess);
        }
      });
    }
    buffer.append(";");
  }

  private static class JVMNameBuffer {
    List<JVMName> myList = new ArrayList<JVMName>();

    public void append(@NotNull JVMName evaluator){
      myList.add(evaluator);
    }

    public void append(char name){
      append(Character.toString(name));
    }

    public void append(String text){
      myList.add(getJVMRawText(text));
    }

    public JVMName toName() {
      final List<JVMName> optimised = new ArrayList<JVMName>();
      for (JVMName evaluator : myList) {
        if (evaluator instanceof JVMRawText && !optimised.isEmpty() && optimised.get(optimised.size() - 1) instanceof JVMRawText) {
          JVMRawText nameEvaluator = (JVMRawText)optimised.get(optimised.size() - 1);
          nameEvaluator.setName(nameEvaluator.getName() + ((JVMRawText)evaluator).getName());
        }
        else {
          optimised.add(evaluator);
        }
      }

      if(optimised.size() == 1) return optimised.get(0);
      if(optimised.isEmpty()) return new JVMRawText("");

      return new JVMName() {
        String myName = null;
        public String getName(DebugProcessImpl process) throws EvaluateException {
          if(myName == null){
            String name = "";
            for (JVMName nameEvaluator : optimised) {
              name += nameEvaluator.getName(process);
            }
            myName = name;
          }
          return myName;
        }

        public String getDisplayName(DebugProcessImpl debugProcess) {
          if(myName == null) {
            String displayName = "";
            for (JVMName nameEvaluator : optimised) {
              displayName += nameEvaluator.getDisplayName(debugProcess);
            }
            return displayName;
          }
          return myName;
        }
      };
    }
  }

  private static class JVMRawText implements JVMName {
    private String myText;

    public JVMRawText(String text) {
      myText = text;
    }

    public String getName(DebugProcessImpl process) throws EvaluateException {
      return myText;
    }

    public String getDisplayName(DebugProcessImpl debugProcess) {
      return myText;
    }

    public String getName() {
      return myText;
    }

    public void setName(String name) {
      myText = name;
    }
  }

  private static class JVMClassAt implements JVMName {
    private final SourcePosition mySourcePosition;

    public JVMClassAt(SourcePosition sourcePosition) {
      mySourcePosition = sourcePosition;
    }

    public String getName(DebugProcessImpl process) throws EvaluateException {
      List<ReferenceType> allClasses = process.getPositionManager().getAllClasses(mySourcePosition);
      if(!allClasses.isEmpty()) {
        return allClasses.get(0).name();
      }

      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("error.class.not.loaded", getDisplayName(process)));
    }

    public String getDisplayName(final DebugProcessImpl debugProcess) {
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          return getSourcePositionClassDisplayName(debugProcess, mySourcePosition);
        }
      });
    }
  }

  public static JVMName getJVMRawText(String qualifiedName) {
    return new JVMRawText(qualifiedName);
  }

  public static JVMName getJVMQualifiedName(PsiType psiType) {
    if(psiType instanceof PsiArrayType) {
      final PsiArrayType arrayType = (PsiArrayType)psiType;
      JVMName jvmName = getJVMQualifiedName(arrayType.getComponentType());
      JVMNameBuffer buffer = new JVMNameBuffer();
      buffer.append(jvmName);
      buffer.append("[]");
      return buffer.toName();
    }

    PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
    if (psiClass == null) {
      return getJVMRawText(psiType.getCanonicalText());
    } 
    else {
      return getJVMQualifiedName(psiClass);
    }
  }
                               
  public static JVMName getJVMQualifiedName(PsiClass psiClass) {
    if (!PsiUtil.isLocalOrAnonymousClass(psiClass)) {
      final String name = getNonAnonymousClassName(psiClass);
      if (name != null) {
        return getJVMRawText(name);
      }
    }
    return new JVMClassAt(SourcePosition.createFromElement(psiClass));
  }

  @Nullable
  public static JVMName getContextClassJVMQualifiedName(@Nullable SourcePosition pos) {
    if (pos == null) {
      return null;
    }
    final PsiClass psiClass = getClassAt(pos);
    if (psiClass == null) {
      return null;
    }
    if (!PsiUtil.isLocalOrAnonymousClass(psiClass)) {
      final String name = getNonAnonymousClassName(psiClass);
      if (name != null) {
        return getJVMRawText(name);
      }
    }
    return new JVMClassAt(pos);
  }

  @Nullable
  public static String getNonAnonymousClassName(PsiClass aClass) {
    PsiClass parentClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
    if(parentClass != null) {
      final String parentName = getNonAnonymousClassName(parentClass);
      if (parentName == null) {
        return null;
      }
      return parentName + "$" + aClass.getName();
    }
    return DebuggerManager.getInstance(aClass.getProject()).getVMClassQualifiedName(aClass);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static JVMName getJVMSignature(PsiMethod method) {
    JVMNameBuffer signature = new JVMNameBuffer();
    signature.append("(");
    
    if (method.isConstructor()) {
      final PsiClass declaringClass = method.getContainingClass();
      if (declaringClass != null) {
        final PsiClass outerClass = declaringClass.getContainingClass();
        if (outerClass != null) {
          // declaring class is an inner class
          if (!declaringClass.hasModifierProperty(PsiModifier.STATIC)) {
            appendJvmClassQualifiedName(signature, getJVMQualifiedName(outerClass));
          }
        }
      }
    }
    for (PsiParameter psiParameter : method.getParameterList().getParameters()) {
      appendJVMSignature(signature, psiParameter.getType());
    }
    signature.append(")");
    if (!method.isConstructor()) {
      appendJVMSignature(signature, method.getReturnType());
    }
    else {
      signature.append(new JVMRawText("V"));
    }
    return signature.toName();
  }

  @Nullable
  public static PsiClass getClassAt(SourcePosition position) {
    final PsiElement element = position.getElementAt();
    return (element != null) ? PsiTreeUtil.getParentOfType(element, PsiClass.class, false) : null;
  }

  @Nullable
  public static String getSourcePositionClassDisplayName(DebugProcessImpl debugProcess, SourcePosition position) {
    if (position == null) {
      return null;
    }
    final PsiFile positionFile = position.getFile();
    if (positionFile instanceof JspFile) {
      return positionFile.getName();
    }

    final PsiClass psiClass = getClassAt(position);

    if(psiClass != null) {
      final String qName = psiClass.getQualifiedName();
      if(qName != null) {
        return qName;
      }
    }

    if(debugProcess != null && debugProcess.isAttached()) {
      List<ReferenceType> allClasses = debugProcess.getPositionManager().getAllClasses(position);
      if(!allClasses.isEmpty()) {
        return allClasses.get(0).name();
      }
    }
    if (psiClass == null) {
      if (positionFile instanceof PsiClassOwner) {
        return positionFile.getName();
      }

      return DebuggerBundle.message("string.file.line.position", positionFile.getName(), position.getLine());
    }
    return calcClassDisplayName(psiClass);
  }

  static String calcClassDisplayName(final PsiClass aClass) {
    final String qName = aClass.getQualifiedName();
    if (qName != null)  {
      return qName;
    }
    final PsiClass parent = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
    if (parent == null) {
      return null;
    }
    
    final String name = aClass.getName();
    if (name != null) {
      return calcClassDisplayName(parent) + "$" + name;
    }
    
    final Ref<Integer> classIndex = new Ref<Integer>(0);
    try {
        parent.accept(new JavaRecursiveElementVisitor() {
          public void visitAnonymousClass(PsiAnonymousClass cls) {
            classIndex.set(classIndex.get() + 1);
            if (aClass.equals(cls)) {
              throw new ProcessCanceledException();
            }
          }
        });
      }
      catch (ProcessCanceledException ignored) {
      }
    return calcClassDisplayName(parent) + "$" + classIndex.get();
  }

  @Nullable
  public static String getSourcePositionPackageDisplayName(DebugProcessImpl debugProcess, SourcePosition position) {
    if (position == null) {
      return null;
    }
    final PsiFile positionFile = position.getFile();
    if (positionFile instanceof JspFile) {
      final PsiDirectory dir = positionFile.getContainingDirectory();
      return dir != null? dir.getVirtualFile().getPresentableUrl() : null;
    }

    final PsiClass psiClass = getClassAt(position);

    if(psiClass != null) {
      PsiClass toplevel = PsiUtil.getTopLevelClass(psiClass);
      if(toplevel != null) {
        String qName = toplevel.getQualifiedName();
        if (qName != null) {
          int i = qName.lastIndexOf('.');
          return i > 0 ? qName.substring(0, i) : "";
        }
      }
    }

    if(debugProcess != null && debugProcess.isAttached()) {
      List<ReferenceType> allClasses = debugProcess.getPositionManager().getAllClasses(position);
      if(!allClasses.isEmpty()) {
        final String className = allClasses.get(0).name();
        int dotIndex = className.lastIndexOf('.');
        if (dotIndex >= 0) {
          return className.substring(0, dotIndex);
        }
      }
    }
    return "";
  }

  public static PsiClass getTopLevelParentClass(PsiClass psiClass) {
    PsiClass enclosing = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class, true);
    while (enclosing != null) {
      psiClass = enclosing;
      enclosing = PsiTreeUtil.getParentOfType(enclosing, PsiClass.class, true); 
    }
    return psiClass;
  }

}
