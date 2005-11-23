/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.uiDesigner.compiler;

import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.Type;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 23.11.2005
 * Time: 13:34:07
 * To change this template use File | Settings | File Templates.
 */
public class RectanglePropertyCodeGenerator extends PropertyCodeGenerator {
  private static Type myRectangleType = Type.getType(Rectangle.class);

  public void generatePushValue(final GeneratorAdapter generator, final Object value) {
    final Rectangle rc = (Rectangle) value;
    generator.newInstance(myRectangleType);
    generator.dup();
    generator.push(rc.x);
    generator.push(rc.y);
    generator.push(rc.width);
    generator.push(rc.height);
    generator.invokeConstructor(myRectangleType, Method.getMethod("void <init>(int,int,int,int)"));
  }
}
