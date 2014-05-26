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
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.signature.SignatureReader;
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor;

import java.util.Map;

public class Util {

  public static MostlySingularMultiMap<String, AnnotationData> makeAnnotations(Map<Key, Value> solutions, Map<Method, MethodExtra> extras) {
    MostlySingularMultiMap<String, AnnotationData> annotations = new MostlySingularMultiMap<String, AnnotationData>();
    HashMap<String, StringBuilder> contracts = new HashMap<String, StringBuilder>();
    for (Map.Entry<Key, Value> solution : solutions.entrySet()) {
      Key key = solution.getKey();

      Value value = solution.getValue();
      if (value == Value.Top || value == Value.Bot) {
        continue;
      }

      Direction direction = key.direction;

      String annKey = annotationKey(key.method, extras.get(key.method), direction);
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

  public static String annotationKey(Method method, MethodExtra extra, Direction dir) {
    String annPrefix = annotationKey(method, extra);
    if (dir instanceof In) {
      return annPrefix + " " + ((In)dir).paramIndex;
    } else {
      return annPrefix;
    }
  }

  public static String annotationKey(Method method, MethodExtra extra) {
    if ("<init>".equals(method.methodName)) {
      return canonical(method.internalClassName) + " " +
             simpleName(method.internalClassName) +
             parameters(method, extra);
    } else {
      return canonical(method.internalClassName) + " " +
             returnType(method, extra) + " " +
             method.methodName +
             parameters(method, extra);
    }
  }

  private static String returnType(Method method, MethodExtra extra) {
    if (extra.signature != null) {
      final StringBuilder sb = new StringBuilder();
      new SignatureReader(extra.signature).accept(new SignatureVisitor(Opcodes.ASM5) {
        @Override
        public SignatureVisitor visitReturnType() {
          return new GenericTypeRenderer(sb);
        }
      });
      return sb.toString();
    }
    else {
      return canonical(Type.getReturnType(method.methodDesc).getClassName());
    }
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

  private static String parameters(Method method, MethodExtra extra) {
    String result;
    if (extra.signature != null) {
      GenericMethodParametersRenderer renderer = new GenericMethodParametersRenderer();
      new SignatureReader(extra.signature).accept(renderer);
      result = renderer.parameters();
    }
    else {
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
      result = sb.toString();
    }
    if ((extra.access & Opcodes.ACC_VARARGS) != 0) {
      return result.replace("[])", "...)");
    } else {
      return result;
    }
  }

  static class GenericMethodParametersRenderer extends SignatureVisitor {

    private StringBuilder sb = new StringBuilder("(");
    private boolean first = true;
    public GenericMethodParametersRenderer() {
      super(Opcodes.ASM5);
    }
    public String parameters() {
      return sb.append(')').toString();
    }

    @Override
    public SignatureVisitor visitParameterType() {
      if (first) {
        first = false;
      }
      else {
        sb.append(", ");
      }
      return new GenericTypeRenderer(sb);
    }
  }

  static class GenericTypeRenderer extends SignatureVisitor {

    final StringBuilder sb;
    private boolean angleBracketOpen = false;

    public GenericTypeRenderer(StringBuilder sb) {
      super(Opcodes.ASM5);
      this.sb = sb;
    }

    private boolean openAngleBracket() {
      if (angleBracketOpen) {
        return false;
      } else {
        angleBracketOpen = true;
        sb.append('<');
        return true;
      }
    }

    private void closeAngleBracket() {
      if (angleBracketOpen) {
        angleBracketOpen = false;
        sb.append('>');
      }
    }

    private void beforeTypeArgument() {
      boolean first = openAngleBracket();
      if (!first) {
        sb.append(',');
      }
    }

    protected void endType() {}

    @Override
    public void visitBaseType(char descriptor) {
      switch (descriptor) {
        case 'V':
          sb.append("void");
          break;
        case 'B':
          sb.append("byte");
          break;
        case 'J':
          sb.append("long");
          break;
        case 'Z':
          sb.append("boolean");
          break;
        case 'I':
          sb.append("int");
          break;
        case 'S':
          sb.append("short");
          break;
        case 'C':
          sb.append("char");
          break;
        case 'F':
          sb.append("float");
          break;
        case 'D':
          sb.append("double");
          break;
      }
      endType();
    }

    @Override
    public void visitTypeVariable(String name) {
      sb.append(name);
      endType();
    }

    @Override
    public SignatureVisitor visitArrayType() {
      return new GenericTypeRenderer(sb) {
        @Override
        protected void endType() {
          sb.append("[]");
        }
      };
    }

    @Override
    public void visitClassType(String name) {
      sb.append(canonical(name));
    }

    @Override
    public void visitInnerClassType(String name) {
      closeAngleBracket();
      sb.append('.').append(canonical(name));
    }

    @Override
    public void visitTypeArgument() {
      beforeTypeArgument();
      sb.append('?');
    }

    @Override
    public SignatureVisitor visitTypeArgument(char wildcard) {
      beforeTypeArgument();
      switch (wildcard) {
        case SignatureVisitor.EXTENDS:
          sb.append("? extends ");
          break;
        case SignatureVisitor.SUPER:
          sb.append("? super ");
          break;
        case SignatureVisitor.INSTANCEOF:
          break;
      }
      return new GenericTypeRenderer(sb);
    }

    @Override
    public void visitEnd() {
      closeAngleBracket();
      endType();
    }

  }
}
