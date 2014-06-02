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
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import org.jetbrains.org.objectweb.asm.Type;

import java.io.IOException;
import java.util.Map;

public class Util {

  static class InternalKey {
    final String annotationKey;
    final Direction dir;

    InternalKey(String annotationKey, Direction dir) {
      this.annotationKey = annotationKey;
      this.dir = dir;
    }
  }

  public static MostlySingularMultiMap<String, AnnotationData> makeAnnotations(TIntObjectHashMap<Value> internalIdSolutions,
                                                                               Enumerators enumerators) {
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
      InternalKey key = null;
      try {
        String s = enumerators.internalKeyEnumerator.valueOf(inKey);
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
          annotations.add(annKey, new AnnotationData("org.jetbrains.annotations.NotNull", ""));
        }
        else if (direction instanceof Out && value == Value.NotNull) {
          annotations.add(baseAnnKey, new AnnotationData("org.jetbrains.annotations.NotNull", ""));
        }
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
      annotations.add(contract.getKey(), new AnnotationData("org.jetbrains.annotations.Contract", contract.getValue().append('"').toString()));
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

  public static String annotationKey(Method method, Direction dir) {
    String annPrefix = annotationKey(method);
    if (dir instanceof In) {
      return annPrefix + " " + ((In)dir).paramIndex;
    } else {
      return annPrefix;
    }
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

}
