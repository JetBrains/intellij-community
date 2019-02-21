// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import static org.jetbrains.annotations.ApiStatus.Experimental;

/**
 * An element in some model, e.g. language model or framework model.
 * <p/>
 * Symbol may be backed by a {@link com.intellij.psi.PsiElement PsiElement} but is not required to. <br/>
 * Symbol may be bound to a {@link com.intellij.openapi.project.Project Project} but is not required to.
 * <p/>
 * Examples:
 * <ul>
 * <li>Java local variable is a symbol in Java language model, it's backed by some PsiElement</li>
 * <li>Spring Bean is a symbol in Spring framework model, it's defined on-the-fly by framework support</li>
 * <li>database column is a Symbol not backed by PsiElement (defined by data source) and not bound to a Project</li>
 * </ul>
 */
@Experimental
public interface Symbol {

}
