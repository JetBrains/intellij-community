/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.classFilesIndex;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.org.objectweb.asm.Type;

import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class AsmUtil {
  private AsmUtil() {}

  public static String getQualifiedClassName(final String name) {
    return StringUtil.replaceChar(Type.getObjectType(name).getClassName(), '$', '.');
  }

  //char
  //double
  //float
  //int
  //long
  //short
  //boolean
  //byte
  //void
  //Object
  //String
  //Class
  private static final Set<String> ASM_PRIMITIVE_TYPES = ContainerUtil
    .newHashSet("C", "D", "F", "I", "J", "S", "Z", "B", "V", "Ljava/lang/Object;", "Ljava/lang/String;", "Ljava/lang/Class;");

  public static boolean isPrimitiveOrArrayOfPrimitives(final String asmType) {
    for (int i = 0; i < asmType.length(); i++) {
      if (asmType.charAt(i) != '[') {
        return ASM_PRIMITIVE_TYPES.contains(asmType.substring(i));
      }
    }
    throw new AssertionError("Illegal string: " + asmType);
  }
}
