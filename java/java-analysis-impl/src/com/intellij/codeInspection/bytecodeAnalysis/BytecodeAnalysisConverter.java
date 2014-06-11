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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentEnumeratorDelegate;
import com.intellij.util.io.PersistentStringEnumerator;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.Type;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisConverter implements ApplicationComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisConverter");

  public static BytecodeAnalysisConverter getInstance() {
    return ApplicationManager.getApplication().getComponent(BytecodeAnalysisConverter.class);
  }

  private PersistentStringEnumerator myPackageEnumerator;
  private PersistentStringEnumerator myNamesEnumerator;
  private PersistentEnumeratorDelegate<int[]> myCompoundKeyEnumerator;

  @Override
  public void initComponent() {
    try {
      File keysDir = new File(PathManager.getIndexRoot(), "bytecodeKeys");
      final File packageKeysFile = new File(keysDir, "packages");
      final File namesFile = new File(keysDir, "names");
      final File compoundKeysFile = new File(keysDir, "compound");
      myPackageEnumerator = new PersistentStringEnumerator(packageKeysFile);
      myNamesEnumerator = new PersistentStringEnumerator(namesFile);
      myCompoundKeyEnumerator = new PersistentEnumeratorDelegate<int[]>(compoundKeysFile, new IntArrayKeyDescriptor(), 1024 * 4);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  public void disposeComponent() {
    try {
      myPackageEnumerator.close();
      myNamesEnumerator.close();
      myCompoundKeyEnumerator.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "BytecodeAnalysisConverter";
  }

  IntIdEquation convert(Equation<Key, Value> equation) throws IOException {
    Result<Key, Value> rhs = equation.rhs;
    IntIdResult result;
    if (rhs instanceof Final) {
      result = new IntIdFinal(((Final<Key, Value>)rhs).value);
    } else {
      Pending<Key, Value> pending = (Pending<Key, Value>)rhs;
      Set<Set<Key>> deltaOrig = pending.delta;
      IntIdComponent[] components = new IntIdComponent[deltaOrig.size()];
      int componentI = 0;
      for (Set<Key> keyComponent : deltaOrig) {
        int[] ids = new int[keyComponent.size()];
        int idI = 0;
        for (Key id : keyComponent) {
          int[] compoundKey = mkCompoundKey(id);
          ids[idI] = myCompoundKeyEnumerator.enumerate(compoundKey);
          idI++;
        }
        IntIdComponent intIdComponent = new IntIdComponent(ids);
        components[componentI] = intIdComponent;
        componentI++;
      }
      result = new IntIdPending(pending.infinum, components);
    }

    int key = myCompoundKeyEnumerator.enumerate(mkCompoundKey(equation.id));
    return new IntIdEquation(key, result);
  }

  @NotNull
  public int[] mkCompoundKey(@NotNull Key key) throws IOException {
    Direction direction = key.direction;
    Method method = key.method;

    Type ownerType = Type.getObjectType(method.internalClassName);
    Type[] argTypes = Type.getArgumentTypes(method.methodDesc);
    Type returnType = Type.getReturnType(method.methodDesc);

    int arity = argTypes.length;
    int[] compoundKey = new int[9 + arity * 2];

    compoundKey[0] = direction.directionId();
    compoundKey[1] = direction.paramId();
    compoundKey[2] = direction.valueId();
    writeType(compoundKey, 3, ownerType);
    writeType(compoundKey, 5, returnType);
    compoundKey[7] = myNamesEnumerator.enumerate(method.methodName);
    compoundKey[8] = argTypes.length;

    for (int i = 0; i < argTypes.length; i++) {
      writeType(compoundKey, 9 + 2*i, argTypes[i]);
    }
    return compoundKey;
  }

  @Nullable
  private static Direction extractDirection(int[] compoundKey) {
    switch (compoundKey[0]) {
      case Direction.OUT_DIRECTION:
        return new Out();
      case Direction.IN_DIRECTION:
        return new In(compoundKey[1]);
      case Direction.INOUT_DIRECTION:
        return new InOut(compoundKey[1], Value.values()[compoundKey[2]]);
    }
    return null;
  }


  private void writeType(int[] compoundKey, int i, Type type) throws IOException {
    String className = type.getClassName();
    int dotIndex = className.lastIndexOf('.');
    String packageName;
    String simpleName;
    if (dotIndex > 0) {
      packageName = className.substring(0, dotIndex);
      simpleName = className.substring(dotIndex + 1);
    } else {
      packageName = "";
      simpleName = className;
    }
    compoundKey[i] = myPackageEnumerator.enumerate(packageName);
    compoundKey[i + 1] = myNamesEnumerator.enumerate(simpleName);
    myPackageEnumerator.valueOf(compoundKey[i]);
    myNamesEnumerator.valueOf(compoundKey[i + 1]);
  }

  @Nullable
  public int[] mkCompoundKey(@NotNull PsiMethod psiMethod, Direction direction) throws IOException {

    final PsiClass psiClass = PsiTreeUtil.getParentOfType(psiMethod, PsiClass.class, false);
    if (psiClass == null) {
      return null;
    }
    PsiClass outerClass = psiClass.getContainingClass();
    boolean isInnerClassConstructor = psiMethod.isConstructor() && (outerClass != null) && !psiClass.hasModifierProperty(PsiModifier.STATIC);
    PsiParameter[] parameters = psiMethod.getParameterList().getParameters();

    final int shift = isInnerClassConstructor ? 1 : 0;
    final int arity = parameters.length + shift;

    int[] compoundKey = new int[9 + arity * 2];
    compoundKey[0] = direction.directionId();
    compoundKey[1] = direction.paramId();
    compoundKey[2] = direction.valueId();
    writeClass(compoundKey, 3, psiClass, 0);

    PsiType returnType = psiMethod.getReturnType();
    if (returnType == null) {
      if (!writeType(compoundKey, 5, PsiType.VOID)) {
        return null;
      }
      compoundKey[7] = myNamesEnumerator.enumerate("<init>");
    } else {
      if (!writeType(compoundKey, 5, returnType)) {
        return null;
      }
      compoundKey[7] = myNamesEnumerator.enumerate(psiMethod.getName());
    }
    compoundKey[8] = arity;
    if (isInnerClassConstructor) {
      writeClass(compoundKey, 9, outerClass, 0);
    }
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (!writeType(compoundKey, 9 + (2 * (i + shift)), parameter.getType())) {
        return null;
      }
    }

    return compoundKey;
  }

  public int mkKey(@NotNull PsiMethod psiMethod, Direction direction) throws IOException {
    int[] compoundKey = mkCompoundKey(psiMethod, direction);
    if (compoundKey == null) {
      return -1;
    }
    else {
      return myCompoundKeyEnumerator.enumerate(compoundKey);
    }
  }

  private void writeClass(int[] compoundKey, int i, PsiClass psiClass, int dimensions) throws IOException {
    String packageName = "";

    PsiClassOwner psiFile = (PsiClassOwner) psiClass.getContainingFile();
    if (psiFile != null) {
      packageName = psiFile.getPackageName();
    }
    String qname = psiClass.getQualifiedName();
    String className = qname;
    if (qname != null && packageName.length() > 0) {
      className = qname.substring(packageName.length() + 1).replace('.', '$');
    }
    compoundKey[i] = myPackageEnumerator.enumerate(packageName);
    if (dimensions == 0) {
      compoundKey[i + 1] = myNamesEnumerator.enumerate(className);
    } else {
      StringBuilder sb = new StringBuilder(className);
      for (int j = 0; j < dimensions; j++) {
        sb.append("[]");
      }
      compoundKey[i + 1] = myNamesEnumerator.enumerate(sb.toString());
    }
  }

  private boolean writeType(int[] compoundKey, int i, PsiType psiType) throws IOException {
    int dimensions = 0;
    psiType = TypeConversionUtil.erasure(psiType);
    if (psiType instanceof PsiArrayType) {
      PsiArrayType arrayType = (PsiArrayType)psiType;
      psiType = arrayType.getDeepComponentType();
      dimensions = arrayType.getArrayDimensions();
    }

    if (psiType instanceof PsiClassType) {
      // no resolve() -> no package/class split
      PsiClass psiClass = ((PsiClassType)psiType).resolve();
      if (psiClass != null) {
        writeClass(compoundKey, i, psiClass, dimensions);
        return true;
      }
      else {
        return false;
      }
    }
    else if (psiType instanceof PsiPrimitiveType) {
      String packageName = "";
      String className = psiType.getPresentableText();
      compoundKey[i] = myPackageEnumerator.enumerate(packageName);
      if (dimensions == 0) {
        compoundKey[i + 1] = myNamesEnumerator.enumerate(className);
      } else {
        StringBuilder sb = new StringBuilder(className);
        for (int j = 0; j < dimensions; j++) {
          sb.append("[]");
        }
        compoundKey[i + 1] = myNamesEnumerator.enumerate(sb.toString());
      }
      return true;
    }
    return false;
  }

  public Annotations makeAnnotations(TIntObjectHashMap<Value> internalIdSolutions) {
    final TIntObjectHashMap<AnnotationData> outs = new TIntObjectHashMap<AnnotationData>();
    final TIntObjectHashMap<AnnotationData> params = new TIntObjectHashMap<AnnotationData>();
    final TIntObjectHashMap<AnnotationData> contracts = new TIntObjectHashMap<AnnotationData>();

    TIntObjectHashMap<StringBuilder> contractBuilders = new TIntObjectHashMap<StringBuilder>();
    TIntObjectIterator<Value> solutionsIterator = internalIdSolutions.iterator();

    for (int i = internalIdSolutions.size(); i-- > 0;) {
      solutionsIterator.advance();
      int key = solutionsIterator.key();
      Value value = solutionsIterator.value();
      if (value == Value.Top || value == Value.Bot) {
        continue;
      }
      int[] compoundKey = null;
      try {
        compoundKey = myCompoundKeyEnumerator.valueOf(key);
      }
      catch (IOException e) {
        // TODO: question: how to react?
      }

      if (compoundKey != null) {
        Direction direction = extractDirection(compoundKey);

        if (direction instanceof In && value == Value.NotNull) {
          params.put(key, new AnnotationData("org.jetbrains.annotations.NotNull", ""));
        }
        else if (direction instanceof Out && value == Value.NotNull) {
          outs.put(key, new AnnotationData("org.jetbrains.annotations.NotNull", ""));
        }
        else if (direction instanceof InOut) {
          compoundKey[0] = 0;
          compoundKey[1] = 0;
          compoundKey[2] = 0;
          try {
            // TODO - sort (normalize) contract clauses
            int baseKey = myCompoundKeyEnumerator.enumerate(compoundKey);
            StringBuilder sb = contractBuilders.get(baseKey);
            if (sb == null) {
              sb = new StringBuilder("\"");
              contractBuilders.put(baseKey, sb);
            }
            else {
              sb.append(';');
            }
            int arity = compoundKey[8];
            contractElement(sb, arity, (InOut)direction, value);
          }
          catch (IOException e) {
            // TODO: question: how to react?
          }
        }
      }
    }

    TIntObjectIterator<StringBuilder> buildersIterator = contractBuilders.iterator();
    for (int i = contractBuilders.size(); i-- > 0;) {
      buildersIterator.advance();
      int key = buildersIterator.key();
      StringBuilder value = buildersIterator.value();
      if (!outs.contains(key)) {
        contracts.put(key, new AnnotationData("org.jetbrains.annotations.Contract", value.append('"').toString()));
      }
    }

    return new Annotations(outs, params, contracts);
  }

  static String contractValueString(Value v) {
    switch (v) {
      case False: return "false";
      case True: return "true";
      case NotNull: return "!null";
      case Null: return "null";
      default: return "_";
    }
  }

  static String contractElement(StringBuilder sb, int arity, InOut inOut, Value value) {
    for (int i = 0; i < arity; i++) {
      Value currentValue = Value.Top;
      if (i == inOut.paramIndex) {
        currentValue = inOut.inValue;
      }
      if (i > 0) {
        sb.append(',');
      }
      sb.append(contractValueString(currentValue));
    }
    sb.append("->");
    sb.append(contractValueString(value));
    return sb.toString();
  }

  public String debugCompoundKey(@NotNull int[] key) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append(key[0]);
    sb.append(", ");
    sb.append(key[1]);
    sb.append(", ");
    sb.append(key[2]);
    sb.append(", ");
    sb.append(myPackageEnumerator.valueOf(key[3]));
    sb.append(", ");
    sb.append(myNamesEnumerator.valueOf(key[4]));
    sb.append(", ");
    sb.append(myPackageEnumerator.valueOf(key[5]));
    sb.append(", ");
    sb.append(myNamesEnumerator.valueOf(key[6]));
    sb.append(", ");
    sb.append(myNamesEnumerator.valueOf(key[7]));
    sb.append(", ");
    sb.append(key[8]);
    sb.append(", ");

    for (int i = 0; i < key[8]; i++) {
      sb.append(myPackageEnumerator.valueOf(key[9 + 2*i]));
      sb.append(", ");
      sb.append(myNamesEnumerator.valueOf(key[9 + 2*i + 1]));
      sb.append(", ");

    }
    return sb.toString();
  }

  private static class IntArrayKeyDescriptor implements KeyDescriptor<int[]> {

    @Override
    public void save(@NotNull DataOutput out, int[] value) throws IOException {
      DataInputOutputUtil.writeINT(out, value.length);
      for (int i : value) {
        DataInputOutputUtil.writeINT(out, i);
      }
    }

    @Override
    public int[] read(@NotNull DataInput in) throws IOException {
      int[] value = new int[DataInputOutputUtil.readINT(in)];
      for (int i = 0; i < value.length; i++) {
        value[i] = DataInputOutputUtil.readINT(in);
        
      }
      return value;
    }

    @Override
    public int getHashCode(int[] value) {
      return Arrays.hashCode(value);
    }

    @Override
    public boolean isEqual(int[] val1, int[] val2) {
      return Arrays.equals(val1, val2);
    }
  }

}
