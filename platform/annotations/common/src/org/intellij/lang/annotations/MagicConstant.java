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
package org.intellij.lang.annotations;

import java.lang.annotation.*;

/**
 * <p>This annotation intended to help IDEA to detect and auto-complete int and String constants used as an enumeration.
 * For example, in the {@link java.awt.Label#Label(String, int)} constructor the <tt><b>alignment</b></tt> parameter can be one of the following
 * int constants: {@link java.awt.Label#LEFT}, {@link java.awt.Label#CENTER} or {@link java.awt.Label#RIGHT}
 *
 * <p>So, if <tt>@MagicConstant</tt> annotation applied to this constructor, IDEA will check the constructor usages for the allowed values.
 * <p>E.g.<br>
 *  <pre>{@code
 * new Label("text", 0); // 0 is not allowed
 * new Label("text", Label.LEFT); // OK
 * }</pre>
 *
 * <p>
 * <tt>@MagicConstant</tt> can be applied to:
 * <ul>
 *  <li> Field, local variable, parameter.
 *
 *  <br>E.g. <br>
 * <pre>{@code @MagicConstant(intValues = {TOP, CENTER, BOTTOM})
 * int textPosition;
 * }</pre>
 * IDEA will check expressions assigned to the variable for allowed values:
 * <pre>{@code
 *  textPosition = 0; // not allowed
 *  textPosition = TOP; // OK
 * }</pre>
 *
 * <li> Method
 *
 * <br>E.g.<br>
 * <pre>{@code @MagicConstant(flagsFromClass = java.lang.reflect.Modifier.class)
 *  public native int getModifiers();
 * }</pre>
 *
 * IDEA will analyse getModifiers() method calls and check if its return value is used with allowed values:<br>
 * <pre>{@code
 *  if (aClass.getModifiers() == 3) // not allowed
 *  if (aClass.getModifiers() == Modifier.PUBLIC) // OK
 * }</pre>
 *
 * <li>Annotation class<br>
 * Annotation class annotated with <tt>@MagicConstant</tt> created alias you can use to annotate
 * everything as if it was annotated with <tt>@MagicConstant</tt> itself.
 *
 * <br>E.g.<br>
 * <pre>{@code @MagicConstant(flags = {Font.PLAIN, Font.BOLD, Font.ITALIC}) }</pre>
 * <pre>{@code @interface FontStyle {} }</pre>
 *
 * IDEA will check constructs annotated with @FontStyle for allowed values:<br>
 * <tt>@FontStyle int myStyle = 3; // not allowed<br></tt>
 * <tt>@FontStyle int myStyle = Font.BOLD | Font.ITALIC; // OK</tt><br>
 *
 * </ul>
 *
 * The <tt>@MagicConstant</tt> annotation has SOURCE retention, i.e. it is removed upon compilation and does not create any runtime overhead.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({
          ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE,
          ElementType.ANNOTATION_TYPE,
          ElementType.METHOD
        })
public @interface MagicConstant {
  /**
   * @return int values (typically named constants) which are allowed here.
   * E.g.
   * <pre><tt>
   * {@code
   * void setConfirmOpenNewProject(@MagicConstant(intValues = {OPEN_PROJECT_ASK, OPEN_PROJECT_NEW_WINDOW, OPEN_PROJECT_SAME_WINDOW})
   *                               int confirmOpenNewProject);
   * }</tt></pre>
   */
  long[] intValues() default {};

  /**
   * @return String values (typically named constants) which are allowed here.
   */
  String[] stringValues() default {};

  /**
   * @return allowed int flags (i.e. values (typically named constants) which can be combined with bitwise OR operator (|).
   * The difference from the {@link #intValues()} is that flags are allowed to be combined (via plus:+ or bitwise OR: |) whereas values aren't.
   * The literals "0" and "-1" are also allowed to denote absence and presense of all flags respectively.
   *
   * E.g.
   * <pre><tt>
   * {@code @MagicConstant(flags = {HierarchyEvent.PARENT_CHANGED,HierarchyEvent.DISPLAYABILITY_CHANGED,HierarchyEvent.SHOWING_CHANGED})
   * int hFlags;
   *
   * hFlags = 3; // not allowed; should be "magic" constant.
   * if (hFlags & (HierarchyEvent.PARENT_CHANGED | HierarchyEvent.SHOWING_CHANGED) != 0); // OK: combined several constants via bitwise OR
   * }</tt></pre>
   */
  long[] flags() default {};

  /**
   * @return allowed values which are defined in the specified class public static final constants.
   *
   * E.g.
   * <pre><tt>
   * {@code @MagicConstant(valuesFromClass = Cursor.class)
   * int cursorType;
   *
   * cursorType = 11; // not allowed; should be "magic" constant.
   * cursorType = Cursor.E_RESIZE_CURSOR; // OK: "magic" constant used.
   * }</tt></pre>
   */
  Class valuesFromClass() default void.class;

  /**
   * @return allowed int flags which are defined in the specified class public static final constants.
   * The difference from the {@link #valuesFromClass()} is that flags are allowed to be combined (via plus:+ or bitwise OR: |) whereas values aren't.
   * The literals "0" and "-1" are also allowed to denote absence and presense of all flags respectively.
   *
   * E.g.
   * <pre><tt>
   * {@code @MagicConstant(flagsFromClass = java.awt.InputEvent.class)
   * int eventMask;
   *
   * eventMask = 10; // not allowed; should be "magic" constant.
   * eventMask = InputEvent.CTRL_MASK | InputEvent.ALT_MASK; // OK: combined several constants via bitwise OR
   * }</tt></pre>
   */
  Class flagsFromClass() default void.class;
}
