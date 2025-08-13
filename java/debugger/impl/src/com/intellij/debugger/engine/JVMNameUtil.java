// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class JVMNameUtil {
  private static final Logger LOG = Logger.getInstance(JVMNameUtil.class);

  public static final String CONSTRUCTOR_NAME = "<init>";

  public static @Nullable String getPrimitiveSignature(String typeName) {
    if (PsiTypes.booleanType().getCanonicalText().equals(typeName)) {
      return "Z";
    }
    else if (PsiTypes.byteType().getCanonicalText().equals(typeName)) {
      return "B";
    }
    else if (PsiTypes.charType().getCanonicalText().equals(typeName)) {
      return "C";
    }
    else if (PsiTypes.shortType().getCanonicalText().equals(typeName)) {
      return "S";
    }
    else if (PsiTypes.intType().getCanonicalText().equals(typeName)) {
      return "I";
    }
    else if (PsiTypes.longType().getCanonicalText().equals(typeName)) {
      return "J";
    }
    else if (PsiTypes.floatType().getCanonicalText().equals(typeName)) {
      return "F";
    }
    else if (PsiTypes.doubleType().getCanonicalText().equals(typeName)) {
      return "D";
    }
    else if (PsiTypes.voidType().getCanonicalText().equals(typeName)) {
      return "V";
    }
    return null;
  }

  private static void appendJVMSignature(JVMNameBuffer buffer, PsiType type) {
    if (type == null) {
      return;
    }
    final PsiType psiType = TypeConversionUtil.erasure(type);
    if (psiType instanceof PsiArrayType) {
      buffer.append(new JVMRawText("["));
      appendJVMSignature(buffer, ((PsiArrayType)psiType).getComponentType());
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
    if (jvmName instanceof JVMRawText) {
      buffer.append(((JVMRawText)jvmName).getName().replace('.', '/'));
    }
    else {
      buffer.append(new JVMName() {
        @Override
        public String getName(DebugProcessImpl process) throws EvaluateException {
          return jvmName.getName(process).replace('.', '/');
        }

        @Override
        public String getDisplayName(DebugProcessImpl debugProcess) {
          return jvmName.getDisplayName(debugProcess);
        }
      });
    }
    buffer.append(";");
  }

  private static class JVMNameBuffer {
    private final List<JVMName> myList = new ArrayList<>();

    public void append(@NotNull JVMName evaluator) {
      myList.add(evaluator);
    }

    public void append(char name) {
      append(Character.toString(name));
    }

    public void append(String text) {
      myList.add(getJVMRawText(text));
    }

    public JVMName toName() {
      final List<JVMName> optimised = new ArrayList<>();
      for (JVMName evaluator : myList) {
        if (evaluator instanceof JVMRawText && !optimised.isEmpty() &&
            optimised.get(optimised.size() - 1) instanceof JVMRawText nameEvaluator) {
          nameEvaluator.setName(nameEvaluator.getName() + ((JVMRawText)evaluator).getName());
        }
        else {
          optimised.add(evaluator);
        }
      }

      if (optimised.size() == 1) return optimised.get(0);
      if (optimised.isEmpty()) return new JVMRawText("");

      return new JVMName() {
        String myName = null;

        @Override
        public String getName(DebugProcessImpl process) throws EvaluateException {
          if (myName == null) {
            String name = "";
            for (JVMName nameEvaluator : optimised) {
              name += nameEvaluator.getName(process);
            }
            myName = name;
          }
          return myName;
        }

        @Override
        public String getDisplayName(DebugProcessImpl debugProcess) {
          if (myName == null) {
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

    JVMRawText(String text) {
      myText = text;
    }

    @Override
    public String getName(DebugProcessImpl process) throws EvaluateException {
      return myText;
    }

    @Override
    public String getDisplayName(DebugProcessImpl debugProcess) {
      return myText;
    }

    public String getName() {
      return myText;
    }

    public void setName(String name) {
      myText = name;
    }

    @Override
    public String toString() {
      return myText;
    }
  }

  private static class JVMClassAt implements JVMName {
    private final SourcePosition mySourcePosition;

    JVMClassAt(SourcePosition sourcePosition) {
      mySourcePosition = sourcePosition;
    }

    @Override
    public String getName(DebugProcessImpl process) throws EvaluateException {
      List<ReferenceType> allClasses = process.getPositionManager().getAllClasses(mySourcePosition);
      // If there are more than one available, try to match by name
      if (allClasses.size() > 1) {
        String name = ReadAction.compute(() -> getClassVMName(getClassAt(mySourcePosition)));
        if (name != null) {
          for (ReferenceType aClass : allClasses) {
          if (Objects.equals(aClass.name(), name)) {
              return name;
            }
          }
        }
        else { // most probably local class - prefer a class with a longer name :)
          String matchingTypeName = allClasses.stream()
            .map(ReferenceType::name)
            .max(Comparator.comparing(String::length))
            .orElse(null);
          if (matchingTypeName != null) {
            return matchingTypeName;
          }
        }
      }
      if (!allClasses.isEmpty()) {
        return allClasses.get(0).name();
      }

      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("error.class.not.loaded", getDisplayName(process)));
    }

    @Override
    public String getDisplayName(final DebugProcessImpl debugProcess) {
      return getSourcePositionClassDisplayName(debugProcess, mySourcePosition);
    }
  }

  public static @NotNull JVMName getJVMRawText(String qualifiedName) {
    return new JVMRawText(qualifiedName);
  }

  public static JVMName getJVMQualifiedName(PsiType psiType) {
    if (psiType instanceof PsiArrayType arrayType) {
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

  public static @NotNull JVMName getJVMQualifiedName(@NotNull PsiClass psiClass) {
    final String name = getClassVMName(psiClass);
    if (name != null) {
      return getJVMRawText(name);
    }
    else {
      return new JVMClassAt(SourcePosition.createFromElement(psiClass));
    }
  }

  public static @Nullable JVMName getContextClassJVMQualifiedName(@Nullable SourcePosition pos) {
    final PsiClass psiClass = getClassAt(pos);
    if (psiClass == null) {
      return null;
    }
    final String name = getNonAnonymousClassName(psiClass);
    if (name != null) {
      return getJVMRawText(name);
    }
    return new JVMClassAt(pos);
  }

  public static @Nullable String getNonAnonymousClassName(@NotNull PsiClass aClass) {
    if (PsiUtil.isLocalOrAnonymousClass(aClass)) {
      return null;
    }
    if (aClass instanceof PsiImplicitClass a) {
      return ClassUtil.getJVMClassName(a);
    }
    String name = aClass.getName();
    if (name == null) {
      return null;
    }
    PsiClass parentClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
    if (parentClass != null) {
      final String parentName = getNonAnonymousClassName(parentClass);
      if (parentName == null) {
        return null;
      }
      return parentName + "$" + name;
    }
    return DebuggerManager.getInstance(aClass.getProject()).getVMClassQualifiedName(aClass);
  }

  public static @NotNull JVMName getJVMConstructorSignature(@Nullable PsiMethod method, @Nullable PsiClass declaringClass) {
    return getJVMSignature(method, true, declaringClass);
  }

  public static @NotNull JVMName getJVMSignature(@NotNull PsiMethod method) {
    return getJVMSignature(method, method.isConstructor(), method.getContainingClass());
  }

  public static @NotNull String getJVMMethodName(@NotNull PsiMethod method) {
    return method.isConstructor() ? CONSTRUCTOR_NAME : method.getName();
  }

  private static @NotNull JVMName getJVMSignature(@Nullable PsiMethod method, boolean constructor, @Nullable PsiClass declaringClass) {
    JVMNameBuffer signature = new JVMNameBuffer();
    signature.append("(");

    if (constructor) {
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
    if (method != null) {
      for (PsiParameter psiParameter : method.getParameterList().getParameters()) {
        appendJVMSignature(signature, psiParameter.getType());
      }
    }
    signature.append(")");
    if (!constructor && method != null) {
      appendJVMSignature(signature, method.getReturnType());
    }
    else {
      signature.append(new JVMRawText("V"));
    }
    return signature.toName();
  }

  public static @Nullable PsiClass getClassAt(@Nullable SourcePosition position) {
    if (position == null) {
      return null;
    }
    final PsiElement element = position.getElementAt();
    return element != null && element.isValid() ? PsiTreeUtil.getParentOfType(element, PsiClass.class, false) : null;
  }

  public static @Nullable String getSourcePositionClassDisplayName(DebugProcessImpl debugProcess, @Nullable SourcePosition position) {
    if (position == null) {
      return null;
    }

    Pair<String, Boolean> res = ReadAction.compute(() -> {
      final PsiFile positionFile = position.getFile();
      if (positionFile instanceof JspFile) {
        return Pair.create(positionFile.getName(), false);
      }

      final PsiClass psiClass = getClassAt(position);

      if (psiClass != null) {
        final String qName = psiClass.getQualifiedName();
        if (qName != null) {
          return Pair.create(qName, false);
        }
      }

      if (psiClass == null) {
        if (positionFile instanceof PsiClassOwner) {
          return Pair.create(positionFile.getName(), true);
        }

        return Pair.create(JavaDebuggerBundle.message("string.file.line.position", positionFile.getName(), position.getLine()), true);
      }
      return Pair.create(calcClassDisplayName(psiClass), true);
    });

    if (res.second && debugProcess != null && debugProcess.isAttached()) {
      List<ReferenceType> allClasses = debugProcess.getPositionManager().getAllClasses(position);
      if (!allClasses.isEmpty()) {
        return allClasses.get(0).name();
      }
    }
    return res.first;
  }

  static String calcClassDisplayName(final PsiClass aClass) {
    final String qName = aClass.getQualifiedName();
    if (qName != null) {
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

    final Ref<Integer> classIndex = new Ref<>(0);
    try {
      parent.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitAnonymousClass(@NotNull PsiAnonymousClass cls) {
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

  public static @Nullable String getSourcePositionPackageDisplayName(DebugProcessImpl debugProcess, @Nullable SourcePosition position) {
    if (position == null) {
      return null;
    }

    String res = ReadAction.compute(() -> {
      final PsiFile positionFile = position.getFile();
      if (positionFile instanceof JspFile) {
        final PsiDirectory dir = positionFile.getContainingDirectory();
        return dir != null ? dir.getVirtualFile().getPresentableUrl() : null;
      }

      final PsiClass psiClass = getClassAt(position);

      if (psiClass != null) {
        PsiClass toplevel = PsiUtil.getTopLevelClass(psiClass);
        if (toplevel != null) {
          String qName = toplevel.getQualifiedName();
          if (qName != null) {
            int i = qName.lastIndexOf('.');
            return i > 0 ? qName.substring(0, i) : "";
          }
        }
      }

      if (positionFile instanceof PsiClassOwner) {
        String name = ((PsiClassOwner)positionFile).getPackageName();
        if (!StringUtil.isEmpty(name)) {
          return name;
        }
      }
      return null;
    });

    if (res == null && debugProcess != null && debugProcess.isAttached()) {
      List<ReferenceType> allClasses = debugProcess.getPositionManager().getAllClasses(position);
      if (!allClasses.isEmpty()) {
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
    return PsiTreeUtil.getTopmostParentOfType(psiClass, PsiClass.class);
  }

  public static @Nullable String getClassVMName(@Nullable PsiClass containingClass) {
    if (containingClass == null) return null;
    return ClassUtil.getBinaryClassName(containingClass);
  }
}
