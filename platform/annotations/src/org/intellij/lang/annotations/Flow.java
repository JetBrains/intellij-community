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

import org.jetbrains.annotations.NonNls;

import java.lang.annotation.*;

/**
 * This annotation assists the 'Data flow to this' feature by describing data flow
 * from the method parameter to the corresponding container (e.g. <code>ArrayList.add(item)</code>)
 * or from the container to the method return value (e.g. <code>Set.toArray()</code>)
 * or between method parameters (e.g. <code>System.arraycopy(array1, 0, array2, length)</code>)
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER, ElementType.METHOD})
public @interface Flow {
  /**
   * Denotes the source of the data flow.<br>
   * Allowed values are:<br>
   *
   * <ul>
   * <li><code>THIS_SOURCE</code> - Means that the data flows from this container.<br>
   *     E.g. annotation for java.util.List method get(index) means the method reads contents of list and returns it.<br>
   *     {@code @Flow(source = THIS_SOURCE) T get(int index);}<br>
   * </li>
   * <li>
   *   <code>this.</code>Field name - means that the data flows from this container some (synthetic) field.<br>
   *   E.g. annotation for java.util.Map.keySet() method here means that it returns data from the map from the field named "keys".<br>
   *   {@code @Flow(source = "this.keys") Set<K> keySet();}
   * </li>
   * </ul>
   * By default, the source() value is:<br>
   * <ul>
   * <li>
   *   {@link #THIS_SOURCE} if the method was annotated, e.g.<br>
   *   {@code @Flow(sourceIsContainer=true, targetIsContainer=true) Object[] Collection.toArray()}<br>
   *   Here the annotation tells us that java.util.Collection.toArray() method<br>
   *   reads the contents of this collection (source=THIS_SOURCE by default) and passes it outside.
   * </li>
   * <li>
   *   Corresponding argument if the method parameter was annotated, e.g.<br>
   *   {@code void List.add(@Flow(targetIsContainer=true) E item)}<br>
   *   Here the annotation tells us that java.util.List.add(E item) method<br>
   *   takes the argument (source="item" by default) and passes it to this collection.
   * </li>
   * </ul>
   */
  String source() default org.intellij.lang.annotations.Flow.DEFAULT_SOURCE;
  @NonNls String DEFAULT_SOURCE = "The method argument (if parameter was annotated) or this container (if instance method was annotated)";
  @NonNls String THIS_SOURCE = "this";

  /**
   * true if the data source is container and we should track not the expression but its contents.<br>
   * E.g. the java.util.ArrayList constructor takes the collection and stores its contents:<br>
   * ArrayList(<tt><pre>{@code @Flow(sourceIsContainer=true, targetIsContainer=true) Collection<? extends E> collection }</pre></tt>) <br>
   * By default it's false.
   */
  boolean sourceIsContainer() default false;

  /**
   * Denotes the destination of the data flow.<br>
   * Allowed values are:<br>
   *
   * <ul>
   * <li><code>THIS_TARGET</code> - Means that the data flows inside this container (of the class the annotated method belongs to).<br>
   *     E.g. annotation for java.util.List method add(element) means the method takes the argument and passes it to this collection.<br>
   *     {@code boolean add(@Flow(target=THIS_TARGET, targetIsContainer=true) E element);}<br>
   * </li>
   * <li>
   *    Parameter name - means the data flows to this parameter.<br>
   *    E.g.<br>
   *    {@code void arraycopy(@Flow(sourceIsContainer=true, target="dest", targetIsContainer=true) Object src, int srcPos, Object dest, int destPos, int length)}<br>
   *    means that java.lang.System.arraycopy() method takes its first argument and passes it to the "dest" parameter.
   * </li>
   * <li>
   *   <code>this.</code>Field name - means that the data flows to this container in some (synthetic) field.<br>
   *   E.g. annotation for java.util.Map.put(key, value) method here means that it takes the argument 'key' and stores the data in some (hidden) field named "keys".<br>
   *   {@code V put(@Flow(target = "this.keys", targetIsContainer=true) K key, V value);}
   * </li>
   * </ul>
   * By default, the target() value is:<br>
   * <ul>
   * <li>
   *   {@link #THIS_TARGET} if the parameter was annotated, e.g.<br>
   *   {@code void List.set(int index, @Flow(targetIsContainer=true) E element)}<br>
   *   Here the annotation tells us that java.util.List.set(index, element) method<br>
   *   reads its second argument 'element' and passes it to this collection (target=THIS_TARGET by default).
   * </li>
   * <li>
   *   {@link #RETURN_METHOD_TARGET} if the method was annotated, e.g.:<br>
   *   {@code @Flow(sourceIsContainer=true) E List.remove(int index)}<br>
   *   Here the annotation tells us that java.util.List.remove(int index) method<br>
   *   returns the data from its collection (target=RETURN_METHOD_TARGET by default).
   * </li>
   * </ul>
   */
  String target() default org.intellij.lang.annotations.Flow.DEFAULT_TARGET;
  @NonNls String DEFAULT_TARGET = "This container (if the parameter was annotated) or the return value (if instance method was annotated)";
  @NonNls String RETURN_METHOD_TARGET = "The return value of this method";
  @NonNls String THIS_TARGET = "this";

  /**
   * true if the data target is container and we should track not the expression but its contents.<br>
   * E.g. the java.lang.System.arraycopy() method parameter 'dest' is actually an array:<br>
   *    {@code void arraycopy(@Flow(sourceIsContainer=true, target="dest", targetIsContainer=true) Object src, int srcPos, Object dest, int destPos, int length)}<br>
   * By default it's false.
   */
  boolean targetIsContainer() default false;
}

