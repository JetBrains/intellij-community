// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
/**
 * <h3>General Overview</h3>
 * {@link com.intellij.model.Symbol} is an element in some model, e.g. language model or framework model.
 * <p>
 * Symbol is not required to be backed by a {@link com.intellij.psi.PsiElement PsiElement},
 * so it is incorrect to try to obtain the PsiElement from a Symbol.
 * Symbol is not required to be bound to a {@link com.intellij.openapi.project.Project Project} as well,
 * meaning the same instance might be shared between projects.
 * </p>
 * <p>
 * Examples:
 * <ul>
 *   <li>
 *     Java local variable is a symbol in Java language model,
 *     it's backed by a PsiVariable element
 *   </li>
 *   <li>
 *     Compiled class is a symbol in JVM model,
 *     it's backed by stubs, and it's not bound to any project.
 *   </li>
 *   <li>
 *     Spring Bean is a Symbol in Spring framework model,
 *     it's defined on-the-fly by framework support (not backed by PsiElement)
 *     and bound to a Project
 *   </li>
 *   <li>
 *     Database column is a Symbol defined by data source (not backed by PsiElement)
 *     and not bound to a Project since DB elements might be shared between projects
 *   </li>
 * </ul>
 * </p>
 *
 * <h3>Declarations</h3>
 * Each symbol may be declared in several places:
 * <ul>
 *   <li>JVM package is a {@code Symbol} with several declarations (split packages)</li>
 *   <li>C# partial class is a {@code Symbol} with several declarations</li>
 *   <li>property key is a {@code Symbol} declared in several files simultaneously</li>
 * </ul>
 * <h3>Actions</h3>
 * <p>
 * The Platform will use {@code Symbol} in different actions
 * such as <i>Go To Declaration</i>, <i>Find Usages</i> or <i>Quick Documentation</i>.
 * </p>
 * <p>
 * There are several paths to obtain a {@code Symbol} from {@code PsiElement}:
 * <ul>
 *   <li>
 *     from a declaration: {@linkplain com.intellij.psi.PsiElement element}
 *     -> {@linkplain com.intellij.model.psi.PsiSymbolDeclaration declaration}
 *     -> {@linkplain com.intellij.model.Symbol symbol}
 *   </li>
 *   <li>
 *     from a reference: {@linkplain com.intellij.psi.PsiElement element}
 *     -> {@linkplain com.intellij.model.psi.PsiSymbolReference reference}
 *     -> {@linkplain com.intellij.model.Symbol symbol}
 *   </li>
 * </ul>
 * </p>
 */
@ApiStatus.Experimental
package com.intellij.model;

import org.jetbrains.annotations.ApiStatus;
