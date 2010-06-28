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
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.text.MessageFormat;
import java.util.*;

/**
 * @author dsl
 */
public class GenerateEqualsHelper implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateEqualsHelper");
  private final PsiClass myClass;
  private final PsiField[] myEqualsFields;
  private final PsiField[] myHashCodeFields;
  private final HashSet<PsiField> myNonNullSet;
  private final PsiElementFactory myFactory;
  private String myParameterName;

  @NonNls private static final String BASE_OBJECT_PARAMETER_NAME = "object";
  @NonNls private static final String BASE_OBJECT_LOCAL_NAME = "that";
  @NonNls private static final String RESULT_VARIABLE = "result";
  @NonNls private static final String TEMP_VARIABLE = "temp";

  private String myClassInstanceName;

  @NonNls private static final HashMap<String, MessageFormat> PRIMITIVE_HASHCODE_FORMAT = new HashMap<String, MessageFormat>();
  private final boolean mySuperHasHashCode;
  private final CodeStyleManager myCodeStyleManager;
  private final JavaCodeStyleManager myJavaCodeStyleManager;
  private final Project myProject;
  private final boolean myCheckParameterWithInstanceof;

  public GenerateEqualsHelper(Project project,
                              PsiClass aClass,
                              PsiField[] equalsFields,
                              PsiField[] hashCodeFields,
                              PsiField[] nonNullFields,
                              boolean useInstanceofToCheckParameterType) {
    myClass = aClass;
    myEqualsFields = equalsFields;
    myHashCodeFields = hashCodeFields;
    myProject = project;
    myCheckParameterWithInstanceof = useInstanceofToCheckParameterType;

    myNonNullSet = new HashSet<PsiField>();
    myNonNullSet.addAll(Arrays.asList(nonNullFields));
    final PsiManager manager = PsiManager.getInstance(project);

    myFactory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

    mySuperHasHashCode = superMethodExists(getHashCodeSignature());
    myCodeStyleManager = CodeStyleManager.getInstance(manager.getProject());
    myJavaCodeStyleManager = JavaCodeStyleManager.getInstance(manager.getProject());
  }

  private static String getUniqueLocalVarName(String base, PsiField[] fields) {
    String id = base;
    int index = 0;
    while (true) {
      if (index > 0) {
        id = base + index;
      }
      index++;
      boolean anyEqual = false;
      for (PsiField equalsField : fields) {
        if (id.equals(equalsField.getName())) {
          anyEqual = true;
          break;
        }
      }
      if (!anyEqual) break;
    }


    return id;
  }

  public void run() {
    try {
      final Collection<PsiMethod> members = generateMembers();
      for (PsiElement member : members) {
        myClass.add(member);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public Collection<PsiMethod> generateMembers() throws IncorrectOperationException {
    PsiMethod equals = null;
    if (myEqualsFields != null && findMethod(myClass, getEqualsSignature(myProject, myClass.getResolveScope())) == null) {
      equals = createEquals();
    }

    PsiMethod hashCode = null;
    if (myHashCodeFields != null && findMethod(myClass, getHashCodeSignature()) == null) {
      if (myHashCodeFields.length > 0) {
        hashCode = createHashCode();
      }
      else {
        if (!mySuperHasHashCode) {
          @NonNls String text = "";
          CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject);
          if (GenerateMembersUtil.shouldAddOverrideAnnotation(myClass, false)) {
            text += "@Override\n";
          }

          text += "public int hashCode() {\nreturn 0;\n}";
          final PsiMethod trivialHashCode = myFactory.createMethodFromText(text, null);
          hashCode = (PsiMethod)myCodeStyleManager.reformat(trivialHashCode);
        }
      }
    }
    if (hashCode != null && equals != null) {
      return Arrays.asList(equals, hashCode);
    }
    else if (equals != null) {
      return Collections.singletonList(equals);
    }
    else if (hashCode != null) {
      return Collections.singletonList(hashCode);
    }
    else {
      return Collections.emptyList();
    }
  }


  private PsiMethod createEquals() throws IncorrectOperationException {
    JavaCodeStyleManager codeStyleManager = myJavaCodeStyleManager;
    final PsiType objectType = PsiType.getJavaLangObject(myClass.getManager(), myClass.getResolveScope());
    String[] nameSuggestions = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, objectType).names;
    final String objectBaseName = nameSuggestions.length > 0 ? nameSuggestions[0] : BASE_OBJECT_PARAMETER_NAME;
    myParameterName = getUniqueLocalVarName(objectBaseName, myEqualsFields);
    final PsiType classType = myFactory.createType(myClass);
    nameSuggestions = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, classType).names;
    String instanceBaseName = nameSuggestions.length > 0 && nameSuggestions[0].length() < 10 ? nameSuggestions[0] : BASE_OBJECT_LOCAL_NAME;
    myClassInstanceName = getUniqueLocalVarName(instanceBaseName, myEqualsFields);

    @NonNls StringBuffer buffer = new StringBuffer();
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject);
    if (GenerateMembersUtil.shouldAddOverrideAnnotation(myClass, false)) {
      buffer.append("@Override\n");
    }
    buffer.append("public boolean equals(Object ").append(myParameterName).append(") {\n");
    addEqualsPrologue(buffer);
    if (myEqualsFields.length > 0) {
      addClassInstance(buffer);

      ArrayList<PsiField> equalsFields = new ArrayList<PsiField>();
      equalsFields.addAll(Arrays.asList(myEqualsFields));
      Collections.sort(equalsFields, EqualsFieldsComparator.INSTANCE);

      for (PsiField field : equalsFields) {
        if (!field.hasModifierProperty(PsiModifier.STATIC)) {
          final PsiType type = field.getType();
          if (type instanceof PsiArrayType) {
            addArrayEquals(buffer, field);
          }
          else if (type instanceof PsiPrimitiveType) {
            if (PsiType.DOUBLE.equals(type) || PsiType.FLOAT.equals(type)) {
              addDoubleFieldComparison(buffer, field);
            }
            else {
              addPrimitiveFieldComparison(buffer, field);
            }
          }
          else {
            if (type instanceof PsiClassType) {
              final PsiClass aClass = ((PsiClassType)type).resolve();
              if (aClass != null && aClass.isEnum()) {
                addPrimitiveFieldComparison(buffer, field);
                continue;
              }
            }
            addFieldComparison(buffer, field);
          }
        }
      }
    }
    buffer.append("\nreturn true;\n}");
    PsiMethod result = myFactory.createMethodFromText(buffer.toString(), null);
    final PsiParameter parameter = result.getParameterList().getParameters()[0];
    PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, styleSettings.GENERATE_FINAL_PARAMETERS);

    PsiMethod method = (PsiMethod)myCodeStyleManager.reformat(result);
    method = (PsiMethod)myJavaCodeStyleManager.shortenClassReferences(method);
    return method;
  }

  private void addDoubleFieldComparison(final StringBuffer buffer, final PsiField field) {
    @NonNls final String type = PsiType.DOUBLE.equals(field.getType()) ? "Double" : "Float";
    final Object[] parameters = new Object[]{type, myClassInstanceName, field.getName()};
    DOUBLE_FIELD_COMPARER_MF.format(parameters, buffer, null);
  }

  @NonNls private static final MessageFormat ARRAY_COMPARER_MF =
    new MessageFormat("if(!java.util.Arrays.equals({1}, {0}.{1})) return false;\n");
  @NonNls private static final MessageFormat FIELD_COMPARER_MF =
    new MessageFormat("if({1}!=null ? !{1}.equals({0}.{1}) : {0}.{1}!= null)return false;\n");
  @NonNls private static final MessageFormat NON_NULL_FIELD_COMPARER_MF = new MessageFormat("if(!{1}.equals({0}.{1}))return false;\n");
  @NonNls private static final MessageFormat PRIMITIVE_FIELD_COMPARER_MF = new MessageFormat("if({1}!={0}.{1})return false;\n");
  @NonNls private static final MessageFormat DOUBLE_FIELD_COMPARER_MF =
    new MessageFormat("if({0}.compare({1}.{2}, {2}) != 0)return false;\n");

  private void addArrayEquals(StringBuffer buffer, PsiField field) {
    final PsiType fieldType = field.getType();
    if (isNestedArray(fieldType)) {
      buffer.append(" ");
      buffer.append(CodeInsightBundle.message("generate.equals.compare.nested.arrays.comment", field.getName()));
      buffer.append("\n");
      return;
    }
    if (isArrayOfObjects(fieldType)) {
      buffer.append(" ");
      buffer.append(CodeInsightBundle.message("generate.equals.compare.arrays.comment"));
      buffer.append("\n");
    }

    ARRAY_COMPARER_MF.format(getComparerFormatParameters(field), buffer, null);
  }

  private Object[] getComparerFormatParameters(PsiField field) {
    return new Object[]{myClassInstanceName, field.getName()};
  }


  private void addFieldComparison(StringBuffer buffer, PsiField field) {
    boolean canBeNull = !myNonNullSet.contains(field);
    if (canBeNull) {
      FIELD_COMPARER_MF.format(getComparerFormatParameters(field), buffer, null);
    }
    else {
      NON_NULL_FIELD_COMPARER_MF.format(getComparerFormatParameters(field), buffer, null);
    }
  }

  private void addPrimitiveFieldComparison(StringBuffer buffer, PsiField field) {
    PRIMITIVE_FIELD_COMPARER_MF.format(getComparerFormatParameters(field), buffer, null);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private void addInstanceOfToText(@NonNls StringBuffer buffer, String returnValue) {
    if (myCheckParameterWithInstanceof) {
      buffer.append("if(!(").append(myParameterName).append(" instanceof ").append(myClass.getName())
        .append(")) " + "return ").append(returnValue).append(";\n");
    }
    else {
      buffer.append("if(").append(myParameterName).append("== null || getClass() != ").append(myParameterName)
        .append(".getClass()) " + "return ").append(returnValue).append(";\n");
    }
  }

  private void addEqualsPrologue(@NonNls StringBuffer buffer) {
    buffer.append("if(this==");
    buffer.append(myParameterName);
    buffer.append(") return true;\n");
    if (!superMethodExists(getEqualsSignature(myProject, myClass.getResolveScope()))) {
      addInstanceOfToText(buffer, Boolean.toString(false));
    }
    else {
      addInstanceOfToText(buffer, Boolean.toString(false));
      buffer.append("if(!super.equals(");
      buffer.append(myParameterName);
      buffer.append(")) return false;\n");
    }
  }

  private void addClassInstance(@NonNls StringBuffer buffer) {
    buffer.append("\n");
    // A a = (A) object;
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myCodeStyleManager.getProject());
    if (settings.GENERATE_FINAL_LOCALS) {
      buffer.append("final ");
    }

    buffer.append(myClass.getName());
    buffer.append(" ").append(myClassInstanceName).append(" = (");
    buffer.append(myClass.getName());
    buffer.append(")");
    buffer.append(myParameterName);
    buffer.append(";\n\n");
  }


  private boolean superMethodExists(MethodSignature methodSignature) {
    LOG.assertTrue(myClass.isValid());
    PsiMethod superEquals = MethodSignatureUtil.findMethodBySignature(myClass, methodSignature, true);
    if (superEquals == null) return true;
    if (superEquals.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
    return !CommonClassNames.JAVA_LANG_OBJECT.equals(superEquals.getContainingClass().getQualifiedName());
  }

  private PsiMethod createHashCode() throws IncorrectOperationException {
    @NonNls StringBuilder buffer = new StringBuilder();

    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(myProject);
    if (GenerateMembersUtil.shouldAddOverrideAnnotation(myClass, false)) {
      buffer.append("@Override\n");
    }
    buffer.append("public int hashCode() {\n");
    if (!mySuperHasHashCode && myHashCodeFields.length == 1) {
      PsiField field = myHashCodeFields[0];
      final String tempName = addTempForOneField(field, buffer);
      buffer.append("return ");
      if (field.getType() instanceof PsiPrimitiveType) {
        addPrimitiveFieldHashCode(buffer, field, tempName);
      }
      else {
        addFieldHashCode(buffer, field, false);
      }
      buffer.append(";\n}");
    }
    else if (myHashCodeFields.length > 0) {
      CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myCodeStyleManager.getProject());
      final String resultName = getUniqueLocalVarName(settings.LOCAL_VARIABLE_NAME_PREFIX + RESULT_VARIABLE, myHashCodeFields);

      buffer.append("int ");
      buffer.append(resultName);

      boolean resultAssigned = false;
      boolean resultDeclarationCompleted = false;
      if (mySuperHasHashCode) {
        buffer.append(" = ");
        addSuperHashCode(buffer);
        buffer.append(";\n");
        resultAssigned = true;
        resultDeclarationCompleted = true;
      }
      String tempName = addTempDeclaration(buffer, resultDeclarationCompleted);
      if (tempName != null) {
        resultDeclarationCompleted = true;
      }
      for (PsiField field : myHashCodeFields) {
        addTempAssignment(field, buffer, tempName);
        if (resultDeclarationCompleted) {
          buffer.append(resultName);
        }

        buffer.append(" = ");
        if (resultAssigned) {
          buffer.append("31*");
          buffer.append(resultName);
          buffer.append(" + ");
        }
        if (field.getType() instanceof PsiPrimitiveType) {
          addPrimitiveFieldHashCode(buffer, field, tempName);
        }
        else {
          addFieldHashCode(buffer, field, resultAssigned);
        }
        buffer.append(";\n");
        resultAssigned = true;
        resultDeclarationCompleted = true;
      }
      buffer.append("return ");
      buffer.append(resultName);
      buffer.append(";\n}");
    }
    else {
      buffer.append("return 0;\n}");
    }
    PsiMethod hashCode = myFactory.createMethodFromText(buffer.toString(), null);
    return (PsiMethod)myCodeStyleManager.reformat(hashCode);
  }

  private static void addTempAssignment(PsiField field, StringBuilder buffer, String tempName) {
    if (PsiType.DOUBLE.equals(field.getType())) {
      buffer.append(tempName);
      addTempForDoubleInitialization(field, buffer);
    }
  }

  private static void addTempForDoubleInitialization(PsiField field, @NonNls StringBuilder buffer) {
    buffer.append(" = ");
    buffer.append(field.getName());
    buffer.append(" != +0.0d ? Double.doubleToLongBits(");
    buffer.append(field.getName());
    buffer.append(") : 0L;\n");
  }

  private String addTempDeclaration(@NonNls StringBuilder buffer, boolean resultDeclarationCompleted) {
    for (PsiField hashCodeField : myHashCodeFields) {
      if (PsiType.DOUBLE.equals(hashCodeField.getType())) {
        final String name = getUniqueLocalVarName(TEMP_VARIABLE, myHashCodeFields);
        if (!resultDeclarationCompleted) {
          buffer.append("\n;");
        }
        buffer.append("long ");
        buffer.append(name);
        buffer.append(";\n");
        return name;
      }
    }
    return null;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private String addTempForOneField(PsiField field, StringBuilder buffer) {
    if (PsiType.DOUBLE.equals(field.getType())) {
      final String name = getUniqueLocalVarName(TEMP_VARIABLE, myHashCodeFields);
      CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myCodeStyleManager.getProject());
      if (settings.GENERATE_FINAL_LOCALS) {
        buffer.append("final ");
      }
      buffer.append("long ").append(name);
      addTempForDoubleInitialization(field, buffer);
      return name;
    }
    else {
      return null;
    }
  }

  private static void addPrimitiveFieldHashCode(StringBuilder buffer, PsiField field, String tempName) {
    MessageFormat format = PRIMITIVE_HASHCODE_FORMAT.get(field.getType().getCanonicalText());
    buffer.append(format.format(new Object[]{field.getName(), tempName}));
  }

  private void addFieldHashCode(@NonNls StringBuilder buffer, PsiField field, boolean brace) {
    final String name = field.getName();
    if (myNonNullSet.contains(field)) {
      adjustHashCodeToArrays(buffer, field, name);
    }
    else {
      if (brace) {
        buffer.append("(");
      }
      buffer.append(name);
      buffer.append(" != null ? ");
      adjustHashCodeToArrays(buffer, field, name);
      buffer.append(" : 0");
      if (brace) {
        buffer.append(")");
      }
    }
  }

  private static void adjustHashCodeToArrays(@NonNls StringBuilder buffer, final PsiField field, final String name) {
    if (field.getType() instanceof PsiArrayType && hasArraysHashCode(field)) {
      buffer.append("Arrays.hashCode(");
      buffer.append(name);
      buffer.append(")");
    }
    else {
      buffer.append(name);
      buffer.append(".hashCode()");
    }
  }

  private static boolean hasArraysHashCode(final PsiField field) {
    // the method was added in JDK 1.5 - check for actual method presence rather than language level
    Module module = ModuleUtil.findModuleForPsiElement(field);
    if (module == null) return false;
    PsiClass arraysClass = JavaPsiFacade.getInstance(field.getProject()).findClass("java.util.Arrays", module.getModuleWithLibrariesScope());
    if (arraysClass == null) return false;
    final PsiMethod[] methods = arraysClass.findMethodsByName("hashCode", false);
    return methods.length > 0;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private void addSuperHashCode(StringBuilder buffer) {
    if (mySuperHasHashCode) {
      buffer.append("super.hashCode()");
    }
    else {
      buffer.append("0");
    }
  }


  public void invoke() {
    ApplicationManager.getApplication().runWriteAction(this);
  }

  static PsiMethod findMethod(PsiClass aClass, MethodSignature signature) {
    return MethodSignatureUtil.findMethodBySignature(aClass, signature, false);
  }

  static class EqualsFieldsComparator implements Comparator<PsiField> {
    public static final EqualsFieldsComparator INSTANCE = new EqualsFieldsComparator();

    public int compare(PsiField f1, PsiField f2) {
      if (f1.getType() instanceof PsiPrimitiveType && !(f2.getType() instanceof PsiPrimitiveType)) return -1;
      if (!(f1.getType() instanceof PsiPrimitiveType) && f2.getType() instanceof PsiPrimitiveType) return 1;
      final String name1 = f1.getName();
      final String name2 = f2.getName();
      assert name1 != null && name2 != null;
      return name1.compareTo(name2);
    }
  }

  static {
    initPrimitiveHashcodeFormats();
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private static void initPrimitiveHashcodeFormats() {
    PRIMITIVE_HASHCODE_FORMAT.put("byte", new MessageFormat("(int) {0}"));
    PRIMITIVE_HASHCODE_FORMAT.put("short", new MessageFormat("(int) {0}"));
    PRIMITIVE_HASHCODE_FORMAT.put("int", new MessageFormat("{0}"));
    PRIMITIVE_HASHCODE_FORMAT.put("long", new MessageFormat("(int) ({0} ^ ({0} >>> 32))"));
    PRIMITIVE_HASHCODE_FORMAT.put("boolean", new MessageFormat("({0} ? 1 : 0)"));

    PRIMITIVE_HASHCODE_FORMAT.put("float", new MessageFormat("({0} != +0.0f ? Float.floatToIntBits({0}) : 0)"));
    PRIMITIVE_HASHCODE_FORMAT.put("double", new MessageFormat("(int) ({1} ^ ({1} >>> 32))"));

    PRIMITIVE_HASHCODE_FORMAT.put("char", new MessageFormat("(int) {0}"));
    PRIMITIVE_HASHCODE_FORMAT.put("void", new MessageFormat("0"));
    PRIMITIVE_HASHCODE_FORMAT.put("void", new MessageFormat("({0} ? 1 : 0)"));
  }

  public static boolean isNestedArray(PsiType aType) {
    if (!(aType instanceof PsiArrayType)) return false;
    final PsiType componentType = ((PsiArrayType)aType).getComponentType();
    return componentType instanceof PsiArrayType;
  }

  public static boolean isArrayOfObjects(PsiType aType) {
    if (!(aType instanceof PsiArrayType)) return false;
    final PsiType componentType = ((PsiArrayType)aType).getComponentType();
    final PsiClass psiClass = PsiUtil.resolveClassInType(componentType);
    if (psiClass == null) return false;
    final String qName = psiClass.getQualifiedName();
    return CommonClassNames.JAVA_LANG_OBJECT.equals(qName);
  }

  public static MethodSignature getHashCodeSignature() {
    return MethodSignatureUtil.createMethodSignature("hashCode", PsiType.EMPTY_ARRAY, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
  }

  public static MethodSignature getEqualsSignature(Project project, GlobalSearchScope scope) {
    final PsiClassType javaLangObject = PsiType.getJavaLangObject(PsiManager.getInstance(project), scope);
    return MethodSignatureUtil
      .createMethodSignature("equals", new PsiType[]{javaLangObject}, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
  }
}
