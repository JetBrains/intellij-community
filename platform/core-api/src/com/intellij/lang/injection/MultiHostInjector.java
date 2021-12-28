// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.injection;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Performs language injection inside other PSI elements.
 * <p>
 * E.g. inject SQL inside XML tag text or inject RegExp into Java string literals.
 * <p>
 * These injected fragments are treated by the IDE as separate tiny files in a specific language and all corresponding code insight features
 * (completion, highlighting, navigation) become available there.
 * </p>
 *
 * <p>For the more high-level API consider implementing the
 * {@link com.intellij.lang.injection.general.LanguageInjectionContributor LanguageInjectionContributor} and/or
 * {@link com.intellij.lang.injection.general.LanguageInjectionPerformer LanguageInjectionPerformer} </p>
 *
 * @see com.intellij.psi.PsiLanguageInjectionHost
 * @see MultiHostRegistrar
 * @see com.intellij.lang.injection.general.LanguageInjectionContributor
 */
public interface MultiHostInjector {
  ProjectExtensionPointName<MultiHostInjector> MULTIHOST_INJECTOR_EP_NAME = new ProjectExtensionPointName<>("com.intellij.multiHostInjector");

  /**
   * Provides list of places to inject a language to.
   * <p>
   * For example, to inject "RegExp" language to Java string literal, you can override this method with something like this:
   * <code><pre>
   * class MyRegExpToJavaInjector implements MultiHostInjector {
   *   void getLanguagesToInject(MultiHostRegistrar registrar, PsiElement context) {
   *     if (context instanceof PsiLiteralExpression && looksLikeAGoodPlaceToInject(context)) {
   *       registrar
   *         .startInjecting(REGEXP_LANG)
   *         .addPlace(null,null,context,innerRangeStrippingQuotes(context))
   *         .doneInjecting();
   *     }
   *   }
   * }
   * </pre></code>
   * <p>
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
   * <p>
   * which should be converted to Java:
   * <code><pre>class MyDsl { void foo() { System.out.println(42);} }</pre></code>
   * <p>
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
   * <p>
   * Now, then we look at this XML in the editor, "foo" will feel like a method name
   * and "System.out.println(42);" will look and feel like a method body - with highlighting, completion, goto definitions etc.
   */
  void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context);

  /**
   * @return PSI element types to feed to {@link #getLanguagesToInject(MultiHostRegistrar, PsiElement)}.
   */
  @NotNull
  List<? extends Class<? extends PsiElement>> elementsToInjectIn();
}