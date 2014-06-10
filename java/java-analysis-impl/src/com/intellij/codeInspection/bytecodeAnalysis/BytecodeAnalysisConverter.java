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
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MostlySingularMultiMap;
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
import java.util.Map;
import java.util.Set;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisConverter implements ApplicationComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisConverter");

  public static BytecodeAnalysisConverter getInstance() {
    return ApplicationManager.getApplication().getComponent(BytecodeAnalysisConverter.class);
  }

  private PersistentStringEnumerator myInternalKeyEnumerator;
  private PersistentStringEnumerator myPackageEnumerator;
  private PersistentStringEnumerator myNamesEnumerator;
  private PersistentEnumeratorDelegate<int[]> myCompoundKeyEnumerator;

  @Override
  public void initComponent() {
    try {
      File keysDir = new File(PathManager.getIndexRoot(), "bytecodeKeys");
      final File internalKeysFile = new File(keysDir, "faba.internalIds");
      final File packageKeysFile = new File(keysDir, "faba.packages");
      final File namesFile = new File(keysDir, "faba.names1");
      final File compoundKeysFile = new File(keysDir, "faba.keys");
      myInternalKeyEnumerator = new PersistentStringEnumerator(internalKeysFile);
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
      myInternalKeyEnumerator.close();
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
          // TODO refactor here
          ids[idI] = myInternalKeyEnumerator.enumerate(internalKeyString(id));
          idI++;
        }
        IntIdComponent intIdComponent = new IntIdComponent(ids);
        components[componentI] = intIdComponent;
        componentI++;
      }
      result = new IntIdPending(pending.infinum, components);
    }
    int key = myInternalKeyEnumerator.enumerate(internalKeyString(equation.id));
    return new IntIdEquation(key, result);
  }

  public static class InternalKey {
    final String annotationKey;
    final Direction dir;

    InternalKey(String annotationKey, Direction dir) {
      this.annotationKey = annotationKey;
      this.dir = dir;
    }
  }

  @NotNull
  public int[] mkCompoundKey(@NotNull Key key) throws IOException {
    Direction direction = key.direction;
    Method method = key.method;

    Type ownerType = Type.getType(method.internalClassName);
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

  public String showCompoundKey(@NotNull int[] key) throws IOException {
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
      compoundKey[i + 1] = myNamesEnumerator.enumerate(className);
      return true;
    }
    return false;
  }

  public MostlySingularMultiMap<String, AnnotationData> makeAnnotations(TIntObjectHashMap<Value> internalIdSolutions) {
    MostlySingularMultiMap<String, AnnotationData> annotations = new MostlySingularMultiMap<String, AnnotationData>();
    HashMap<String, StringBuilder> contracts = new HashMap<String, StringBuilder>();
    TIntObjectIterator<Value> iterator = internalIdSolutions.iterator();
    for (int i = internalIdSolutions.size(); i-- > 0;) {
      iterator.advance();
      int inKey = iterator.key();
      Value value = iterator.value();
      if (value == Value.Top || value == Value.Bot) {
        continue;
      }
      InternalKey key;
      try {
        String s = myInternalKeyEnumerator.valueOf(inKey);
        key = readInternalKey(s);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }

      if (key != null) {
        Direction direction = key.dir;
        String baseAnnKey = key.annotationKey;

        if (direction instanceof In && value == Value.NotNull) {
          String annKey = baseAnnKey + " " + ((In)direction).paramIndex;
          // TODO - here
          annotations.add(annKey, new AnnotationData("org.jetbrains.annotations.NotNull", ""));
        }
        else if (direction instanceof Out && value == Value.NotNull) {
          // TODO - here
          annotations.add(baseAnnKey, new AnnotationData("org.jetbrains.annotations.NotNull", ""));
        }
        // TODO - sort (normalize) contract clauses
        else if (direction instanceof InOut) {
          StringBuilder sb = contracts.get(baseAnnKey);
          if (sb == null) {
            sb = new StringBuilder("\"");
            contracts.put(baseAnnKey, sb);
          }
          else {
            sb.append(';');
          }
          contractElement(sb, calculateArity(baseAnnKey), (InOut)direction, value);
        }
      }
    }

    for (Map.Entry<String, StringBuilder> contract : contracts.entrySet()) {
      if (!annotations.containsKey(contract.getKey())) {
        annotations.add(contract.getKey(), new AnnotationData("org.jetbrains.annotations.Contract", contract.getValue().append('"').toString()));
      }
    }
    return annotations;
  }

  // TODO - this is a hack for now
  static int calculateArity(String annotationKey) {
    return annotationKey.split(",").length;
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

  public static String internalKeyString(Key key) {
    return annotationKey(key.method) + ';' + direction2Key(key.direction);
  }

  public static String direction2Key(Direction dir) {
    if (dir instanceof In) {
      return "In:" + ((In)dir).paramIndex;
    } else if (dir instanceof Out) {
      return "Out";
    } else {
      InOut inOut = (InOut)dir;
      return "InOut:" + inOut.paramIndex + ":" + inOut.inValue.name();
    }
  }

  public static InternalKey readInternalKey(String s) {
    String[] parts = s.split(";");
    String annKey = parts[0];
    String[] dirStrings = parts[1].split(":");
    if ("In".equals(dirStrings[0])) {
      return new InternalKey(annKey, new In(Integer.valueOf(dirStrings[1])));
    } else if ("Out".equals(dirStrings[0])) {
      return new InternalKey(annKey, new Out());
    } else {
      return new InternalKey(annKey, new InOut(Integer.valueOf(dirStrings[1]), Value.valueOf(dirStrings[2])));
    }
  }

  public static String annotationKey(Method method) {
    if ("<init>".equals(method.methodName)) {
      return canonical(method.internalClassName) + " " +
             simpleName(method.internalClassName) +
             parameters(method);
    } else {
      return canonical(method.internalClassName) + " " +
             returnType(method) + " " +
             method.methodName +
             parameters(method);
    }
  }

  private static String returnType(Method method) {
    return canonical(Type.getReturnType(method.methodDesc).getClassName());
  }

  public static String canonical(String internalName) {
    return internalName.replace('/', '.').replace('$', '.');
  }

  private static String simpleName(String internalName) {
    String cn = canonical(internalName);
    int lastDotIndex = cn.lastIndexOf('.');
    if (lastDotIndex == -1) {
      return cn;
    } else {
      return cn.substring(lastDotIndex + 1);
    }
  }

  private static String parameters(Method method) {
    Type[] argTypes = Type.getArgumentTypes(method.methodDesc);
    StringBuilder sb = new StringBuilder("(");
    boolean notFirst = false;
    for (Type argType : argTypes) {
      if (notFirst) {
        sb.append(", ");
      }
      else {
        notFirst = true;
      }
      sb.append(canonical(argType.getClassName()));
    }
    sb.append(')');
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
