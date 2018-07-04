/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.lang.injection;

import com.intellij.psi.PsiElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @see com.intellij.psi.PsiLanguageInjectionHost
 * @see MultiHostRegistrar
 */
public interface MultiHostInjector {

  ExtensionPointName<MultiHostInjector> MULTIHOST_INJECTOR_EP_NAME = ExtensionPointName.create("com.intellij.multiHostInjector");

  /**
   * Provides list of places to inject a language to. <br>
   *
   * For example, to inject "RegExp" language to java string literal, you can override this method with something like this:
   * <code><pre>
   * class MyRegExpToJavaInjector implements MultiHostInjector {
   *   void getLanguagesToInject(MultiHostRegistrar registrar, PsiElement context) {
   *     if (context instanceof PsiLiteralExpression && looksLikeAGoodPlaceToInject(context)) {
   *       registrar.startInjecting(REGEXP_LANG).addPlace(null,null,context,innerRangeStrippingQuotes(context));
   *     }
   *   }
   * }
   * </pre></code>
   *
   * Also, we may need to inject into several fragments at once. For example, if we have this really bizarre XML-based DSL:
   * <pre>
   * {@code
   *
   * <myDSL>
   *   <method>
   *     <name>foo</name>
   *     <body>System.out.println(42);</body>
   *   </method>
   * </myDSL>
   * }
   * </pre>
   *
   * which should be converted to Java:
   * <code><pre>class MyDsl { void foo() { System.out.println(42);} }</pre></code>
   *
   * Then we can inject Java into several places at once - method name and its body:
   * <code><pre>
   * class MyBizarreDSLInjector implements MultiHostInjector {
   *   void getLanguagesToInject(MultiHostRegistrar registrar, PsiElement context) {
   *     if (isMethodTag(context)) {
   *       registrar.startInjecting(JavaLanguage.INSTANCE);
   *       // construct class header, method header, inject method name, append code block start
   *       registrar.addPlace("class MyDsl { void ", "() {", context, rangeForMethodName(context));
   *       // inject method body, append closing braces to form a valid Java class structure
   *       registrar.addPlace(null, "}}", context, rangeForBody(context));
   *       registrar.doneInjecting();
   *     }
   *   }
   * }
   * </pre></code>
   *
   * Now, then we look at this XML in the editor, "foo" will feel like a method name
   * and "System.out.println(42);" will look and feel like a method body - with highlighting, completion, goto definitions etc.
   *
   */
  void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context);

  @NotNull
  List<? extends Class<? extends PsiElement>> elementsToInjectIn();
}