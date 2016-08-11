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

/**
 * Specifies some aspects of the method behavior depending on the arguments. Can be used by tools for advanced data flow analysis.
 * Note that this annotation just describes how the code works and doesn't add any functionality by means of code generation.<p>
 *
 * Method contract has the following syntax:<br>
 *  contract ::= (clause ';')* clause<br>
 *  clause ::= args '-&gt;' effect<br>
 *  args ::= ((arg ',')* arg )?<br>
 *  arg ::= value-constraint<br>
 *  value-constraint ::= '_' | 'null' | '!null' | 'false' | 'true'<br>
 *  effect ::= value-constraint | 'fail' <p>
 *
 * The constraints denote the following:<br>
 * <ul>
 * <li> _ - any value
 * <li> null - null value
 * <li> !null - a value statically proved to be not-null
 * <li> true - true boolean value
 * <li> false - false boolean value
 * <li> fail - the method throws an exception, if the arguments satisfy argument constraints
 * </ul>
 * Examples:<p>
 * <code>@Contract("_, null -&gt; null")</code> - method returns null if its second argument is null<br>
 * <code>@Contract("_, null -&gt; null; _, !null -&gt; !null")</code> - method returns null if its second argument is null and not-null otherwise<br>
 * <code>@Contract("true -&gt; fail")</code> - a typical assertFalse method which throws an exception if <code>true</code> is passed to it<br>
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
   * Specifies that the annotated method has no visible side effects, in the following sense.
   * If its return value is not used, removing its invocation won't
   * affect program state and change the semantics. Exception throwing is not considered to be a side effect.
   *
   * "Invisible" side effects (such as logging) that don't affect the "important" program semantics are allowed.<br><br>
   *
   * This annotation may be used for more precise data flow analysis, and
   * to check that the method's return value is actually used in the call place.
   */
  boolean pure() default false;
}
