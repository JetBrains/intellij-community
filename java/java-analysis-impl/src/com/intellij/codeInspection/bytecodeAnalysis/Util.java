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

import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.MostlySingularMultiMap;
import org.jetbrains.org.objectweb.asm.Type;

import java.util.Map;

public class Util {

  public static MostlySingularMultiMap<String, AnnotationData> makeAnnotations(Map<Key, Value> solutions) {
    MostlySingularMultiMap<String, AnnotationData> annotations = new MostlySingularMultiMap<String, AnnotationData>();
    HashMap<String, StringBuilder> contracts = new HashMap<String, StringBuilder>();
    for (Map.Entry<Key, Value> solution : solutions.entrySet()) {
      Key key = solution.getKey();

      Value value = solution.getValue();
      if (value == Value.Top || value == Value.Bot) {
        continue;
      }

      Direction direction = key.direction;

      String annKey = annotationKey(key.method, direction);
      if ((direction instanceof In || direction instanceof Out) && value == Value.NotNull) {
        annotations.add(annKey, new AnnotationData("org.jetbrains.annotations.NotNull", ""));
      }
      else if (direction instanceof InOut) {
        StringBuilder sb = contracts.get(annKey);
        if (sb == null) {
          sb = new StringBuilder("\"");
          contracts.put(annKey, sb);
        } else {
          sb.append(';');
        }
        contractElement(sb, key.method, (InOut)direction, value);
      }
    }
    for (Map.Entry<String, StringBuilder> contract : contracts.entrySet()) {
      annotations.add(contract.getKey(), new AnnotationData("org.jetbrains.annotations.Contract", contract.getValue().append('"').toString()));
    }
    return annotations;
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

  static String contractElement(StringBuilder sb, Method method, InOut inOut, Value value) {
    int arity = Type.getArgumentTypes(method.methodDesc).length;
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

  public static String annotationKey(Method method, Direction dir) {
    String annPrefix = annotationKey(method);
    if (dir instanceof In) {
      return annPrefix + " " + ((In)dir).paramIndex;
    } else {
      return annPrefix;
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

}
