package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
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
  private static void appendJVMSignature(JVMNameBuffer buffer , PsiType type)
    throws EvaluateException {
    final PsiType psiType = TypeConversionUtil.erasure(type);
    if (psiType instanceof PsiArrayType) {
      buffer.append(new JVMRawText("["));
      appendJVMSignature(buffer, ((PsiArrayType) psiType).getComponentType());
    }
    else if (psiType instanceof PsiClassType) {
      buffer.append("L");

      final JVMName jvmName = getJVMQualifiedName(psiType);

      if(jvmName instanceof JVMRawText) {
        buffer.append(((JVMRawText)jvmName).getName().replace('.','/'));
      } else {
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
    else if (psiType instanceof PsiPrimitiveType) {
      buffer.append(getPrimitiveSignature(psiType.getCanonicalText()));
    }
    else {
      LOG.assertTrue(false, "unknown type " + type.getCanonicalText());
    }
  }

  private static class JVMNameBuffer {
    List<JVMName> myList = new ArrayList<JVMName>();

    public void append(JVMName evaluator){
      LOG.assertTrue(evaluator != null);
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
      for (Iterator iterator = myList.iterator(); iterator.hasNext();) {
        JVMName evaluator = (JVMName) iterator.next();
        if(evaluator instanceof JVMRawText && optimised.size() > 0 && optimised.get(optimised.size() - 1) instanceof JVMRawText){
          JVMRawText nameEvaluator = (JVMRawText) optimised.get(optimised.size() - 1);
          nameEvaluator.setName(nameEvaluator.getName() + ((JVMRawText)evaluator).getName());
        } else {
          optimised.add(evaluator);
        }
      }

      if(optimised.size() == 1) return optimised.get(0);
      if(optimised.size() == 0) return new JVMRawText("");

      return new JVMName() {
        String myName = null;
        public String getName(DebugProcessImpl process) throws EvaluateException {
          if(myName == null){
            String name = "";
            for (Iterator iterator = optimised.iterator(); iterator.hasNext();) {
              JVMName nameEvaluator = (JVMName) iterator.next();
              name += nameEvaluator.getName(process);
            }
            myName = name;
          }
          return myName;
        }

        public String getDisplayName(DebugProcessImpl debugProcess) {
          if(myName == null) {
            String displayName = "";
            for (Iterator iterator = optimised.iterator(); iterator.hasNext();) {
              JVMName nameEvaluator = (JVMName) iterator.next();
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
      if(allClasses.size() > 0) {
        return allClasses.get(0).name();
      }

      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("error.class.not.loaded", getDisplayName(process)));
    }

    public String getDisplayName(DebugProcessImpl debugProcess) {
      return getSourcePositionClassDisplayName(debugProcess, mySourcePosition);
    }
  }

  public static JVMName getJVMRawText(String qualifiedName) {
    return new JVMRawText(qualifiedName);
  }

  public static JVMName getJVMQualifiedName(PsiType psiType) {
    if(psiType instanceof PsiArrayType) {
      JVMName jvmName = getJVMQualifiedName(((PsiArrayType)psiType).getComponentType());
      JVMNameBuffer buffer = new JVMNameBuffer();
      buffer.append(jvmName);
      buffer.append("[]");
      return buffer.toName();
    }

    PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
    if (psiClass == null) {
      return getJVMRawText(psiType.getCanonicalText());
    } else {
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
  public static String getNonAnonymousClassName(PsiClass aClass) {
    PsiClass parentClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
    if(parentClass != null) {
      final String parentName = getNonAnonymousClassName(parentClass);
      if (parentName == null) {
        return null;
      }
      return parentName + "$" + aClass.getName();
    }
    return aClass.getQualifiedName();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static JVMName getJVMSignature(PsiMethod method) throws EvaluateException {
    JVMNameBuffer signature = new JVMNameBuffer();
    signature.append("(");
    PsiParameterList paramList = method.getParameterList();
    if (paramList != null) {
      PsiParameter[] params = paramList.getParameters();
      for (int idx = 0; idx < params.length; idx++) {
        PsiParameter psiParameter = params[idx];
        appendJVMSignature(signature, psiParameter.getType());
      }
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

  public static @Nullable PsiClass getClassAt(SourcePosition position) {
    final int offset = position.getOffset();
    if (offset < 0) {
      return null;
    }
    PsiElement element = position.getFile().findElementAt(offset);
    return PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
  }

  public static @Nullable String getSourcePositionClassDisplayName(DebugProcessImpl debugProcess, SourcePosition position) {
    final PsiFile positionFile = position.getFile();
    if (positionFile instanceof JspFile) {
      return positionFile.getName() + ":" + position.getLine();
    }

    final PsiClass psiClass = getClassAt(position);

    if(psiClass != null && psiClass.getQualifiedName() != null) {
      return psiClass.getQualifiedName();
    }

    if(debugProcess != null && debugProcess.isAttached()) {
      List<ReferenceType> allClasses = debugProcess.getPositionManager().getAllClasses(position);
      if(allClasses.size() > 0) {
        return allClasses.get(0).name();
      }
    }
    return DebuggerBundle.message("string.file.line.position", positionFile.getName(), position.getLine());
  }

  public static @Nullable String getSourcePositionPackageDisplayName(DebugProcessImpl debugProcess, SourcePosition position) {
    final PsiFile positionFile = position.getFile();
    if (positionFile instanceof JspFile) {
      final PsiDirectory dir = positionFile.getContainingDirectory();
      return dir != null? dir.getVirtualFile().getPresentableUrl() : null;
    }

    final PsiClass psiClass = getClassAt(position);

    if(psiClass != null) {
      final PsiFile containingFile = psiClass.getContainingFile();
      if(containingFile instanceof PsiJavaFile) {
        return ((PsiJavaFile)containingFile).getPackageName();
      }
    }

    if(debugProcess != null && debugProcess.isAttached()) {
      List<ReferenceType> allClasses = debugProcess.getPositionManager().getAllClasses(position);
      if(allClasses.size() > 0) {
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
    PsiClass result = psiClass;
    PsiClass parent = psiClass;
    for(;parent!= null; parent = PsiTreeUtil.getParentOfType(parent, PsiClass.class, true)) {
      result = parent;
    }
    return result;
  }

}
