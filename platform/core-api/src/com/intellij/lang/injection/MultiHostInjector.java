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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 10, 2007
 * Time: 1:58:39 PM
 */
package com.intellij.lang.injection;

import com.intellij.psi.PsiElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @see com.intellij.psi.PsiLanguageInjectionHost
 */
public interface MultiHostInjector {

  ExtensionPointName<MultiHostInjector> MULTIHOST_INJECTOR_EP_NAME = ExtensionPointName.create("com.intellij.multiHostInjector");

  void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context);
  @NotNull
  List<? extends Class<? extends PsiElement>> elementsToInjectIn();
}