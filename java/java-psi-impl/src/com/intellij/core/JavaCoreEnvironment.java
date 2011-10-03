/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.openapi.Disposable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.impl.JavaPsiFacadeImpl;

/**
 * @author yole
 */
public class JavaCoreEnvironment extends CoreEnvironment {
  public JavaCoreEnvironment(Disposable parentDisposable) {
    super(parentDisposable);
    registerComponentInstance(myProject.getPicoContainer(),
                              JavaPsiFacade.class,
                              new JavaPsiFacadeImpl(myProject, myPsiManager, null, null));
  }
}
