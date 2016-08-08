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

package com.intellij.codeInsight.daemon;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;

/**
 * Allows to disable highlighting of certain elements as unused when such elements are not referenced
 * from the code but are referenced in some other way. For example,
 * <ul>
 * <li>from generated code</li>
 * <li>from outside containers: {@code @javax.servlet.annotation.WebServlet public class MyServlet {}}</li>
 * <li>from some frameworks: {@code @javax.ejb.EJB private DataStore myInjectedDataStore;}</li> etc
 * </ul>
 * @author yole
 * @since 6.0
 */
public interface ImplicitUsageProvider {
  ExtensionPointName<ImplicitUsageProvider> EP_NAME = new ExtensionPointName<>("com.intellij.implicitUsageProvider");

  boolean isImplicitUsage(PsiElement element);
  boolean isImplicitRead(PsiElement element);
  boolean isImplicitWrite(PsiElement element);
}
