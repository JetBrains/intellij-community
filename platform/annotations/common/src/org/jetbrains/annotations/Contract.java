/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.annotations;

import java.lang.annotation.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Specifies some aspects of the method behavior depending on the arguments. Can be used by tools for advanced data flow analysis.
 * Note that this annotation just describes how the code works and doesn't add any functionality by means of code generation.<p>
 *
 * Method contract has the following syntax:<br>
 * <pre>{@code
 *  contract ::= (clause ';')* clause
 *  clause ::= args '->' effect
 *  args ::= ((arg ',')* arg )?
 *  arg ::= value-constraint
 *  value-constraint ::= '_' | 'null' | '!null' | 'false' | 'true'
 *  effect ::= value-constraint | 'fail' | 'this' | 'new' | 'param<N>'}</pre> <p>
 *
 * The constraints denote the following:<br>
 * <ul>
 * <li> _ - any value
 * <li> null - null value
 * <li> !null - a value statically proved to be not-null
 * <li> true - true boolean value
 * <li> false - false boolean value
 * </ul>
 *
 * The additional return values denote the following:<br>
 * <ul>
 * <li> fail - the method throws an exception, if the arguments satisfy argument constraints
 * <li> new - (supported in IntelliJ IDEA since version 2018.2) the method returns a non-null new object which is distinct from any other object existing in the heap prior to method execution. If method is also pure, then we can be sure that the new object is not stored to any field/array and will be lost if method return value is not used.
 * <li> this - (supported in IntelliJ IDEA since version 2018.2) the method returns its qualifier value (not applicable for static methods)
 * <li> param1, param2, ... - (supported in IntelliJ IDEA since version 2018.2) the method returns its first (second, ...) parameter value
 * </ul>
 * Examples:<p>
 * {@code @Contract("_, null -> null")} - the method returns null if its second argument is null<br>
 * {@code @Contract("_, null -> null; _, !null -> !null")} - the method returns null if its second argument is null and not-null otherwise<br>
 * {@code @Contract("true -> fail")} - a typical {@code assertFalse} method which throws an exception if {@code true} is passed to it<br>
 * {@code @Contract("_ -> this")} - the method always returns its qualifier (e.g. {@link StringBuilder#append(String)}).
 * {@code @Contract("null -> fail; _ -> param1")} - the method throws an exception if the first argument is null,
 * otherwise it returns the first argument (e.g. {@code Objects.requireNonNull}).<br>
 * {@code @Contract("!null, _ -> param1; null, !null -> param2; null, null -> fail")} - the method returns the first non-null argument,
 * or throws an exception if both arguments are null (e.g. {@code Objects.requireNonNullElse} in Java 9).<br>
 *
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Contract {
  /**
   * Contains the contract clauses describing causal relations between call arguments and the returned value
   */
  String value() default "";

  /**
   * Specifies that the annotated method has no visible side effects.
   * If its return value is not used, removing its invocation won't
   * affect program state and change the semantics, unless method call throws an exception.
   * Exception throwing is not considered to be a side effect.
   * <p>
   * Method should not be marked as pure if it does not produce a side-effect by itself,
   * but it could be used to establish a happens-before relation between an event in
   * another thread, so changes performed in another thread might become visible in current thread
   * after this method invocation. Examples of such methods are {@link Object#wait()}, {@link Thread#join()}
   * or {@link AtomicBoolean#get()}. On the other hand, some synchronized methods like {@link java.util.Vector#get(int)}
   * could be marked as pure, because the purpose of synchronization here is to keep the collection internal integrity
   * rather than to wait for an event in another thread.
   * <p>
   * "Invisible" side effects (such as logging) that don't affect the "important" program semantics are allowed.<br><br>
   * <p>
   * This annotation may be used for more precise data flow analysis, and
   * to check that the method's return value is actually used in the call place.
   */
  boolean pure() default false;

  /**
   * Contains a specifier which describes which method parameters can be mutated during the method call.
   * <p>
   * The following values are possible:
   * <table summary="">
   *   <tr><td>"this"</td><td>Method mutates the receiver object, and doesn't mutates any objects passed as arguments (cannot be applied for static method or constructor)</td></tr>
   *   <tr><td>"param"</td><td>Method mutates the sole argument and doesn't mutate the receiver object (if applicable)</td></tr>
   *   <tr><td>"param1", "param2", ...</td><td>Method mutates the N-th argument</td></tr>
   *   <tr><td>"this,param1"</td><td>Method mutates the receiver and first argument and doesn't mutate any other arguments</td></tr>
   * </table>
   *
   * <strong>Warning: This annotation parameter is experimental and may be changed or removed without further notice!</strong>
   * @return a mutation specifier string
   */
  String mutates() default "";
}
