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
 * User: anna
 * Date: 18-Dec-2007
 */
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension;
import com.intellij.codeInspection.reference.RefClass;
import com.intellij.codeInspection.reference.RefField;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public abstract class GlobalJavaInspectionContext implements GlobalInspectionContextExtension<GlobalJavaInspectionContext> {
  public static final Key<GlobalJavaInspectionContext> CONTEXT = Key.create("GlobalJavaInspectionContext");

  public interface DerivedClassesProcessor extends Processor<PsiClass> {
  }

  public interface DerivedMethodsProcessor extends Processor<PsiMethod> {
  }

  public interface UsagesProcessor extends Processor<PsiReference> {
  }

  /**
   * Requests that usages of the specified class outside the current analysis
   * scope be passed to the specified processor.
   *
   * @param refClass the reference graph node for the class whose usages should be processed.
   * @param p        the processor to pass the usages to.
   */
  public abstract void enqueueClassUsagesProcessor(RefClass refClass, UsagesProcessor p);

  /**
   * Requests that derived classes of the specified class outside the current analysis
   * scope be passed to the specified processor.
   *
   * @param refClass the reference graph node for the class whose derived classes should be processed.
   * @param p        the processor to pass the classes to.
   */
  public abstract void enqueueDerivedClassesProcessor(RefClass refClass, DerivedClassesProcessor p);

  /**
   * Requests that implementing or overriding methods of the specified method outside
   * the current analysis scope be passed to the specified processor.
   *
   * @param refMethod the reference graph node for the method whose derived methods should be processed.
   * @param p         the processor to pass the methods to.
   */
  public abstract void enqueueDerivedMethodsProcessor(RefMethod refMethod, DerivedMethodsProcessor p);

  /**
   * Requests that usages of the specified field outside the current analysis
   * scope be passed to the specified processor.
   *
   * @param refField the reference graph node for the field whose usages should be processed.
   * @param p        the processor to pass the usages to.
   */
  public abstract void enqueueFieldUsagesProcessor(RefField refField, UsagesProcessor p);

  /**
   * Requests that usages of the specified method outside the current analysis
   * scope be passed to the specified processor.
   *
   * @param refMethod the reference graph node for the method whose usages should be processed.
   * @param p         the processor to pass the usages to.
   */
  public abstract void enqueueMethodUsagesProcessor(RefMethod refMethod, UsagesProcessor p);

  public abstract EntryPointsManager getEntryPointsManager(RefManager manager);

  @NotNull
  @Override
  public Key<GlobalJavaInspectionContext> getID() {
    return CONTEXT;
  }
}
