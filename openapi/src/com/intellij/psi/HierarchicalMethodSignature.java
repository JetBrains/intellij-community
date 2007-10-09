/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Data structure which allows efficient retrieval of super methods for a Java method.
 *
 * @author ven
 * @since 5.1
 */
public abstract class HierarchicalMethodSignature extends MethodSignatureBackedByPsiMethod {

  public HierarchicalMethodSignature(final MethodSignatureBackedByPsiMethod signature) {
    super(signature.getMethod(), signature.getSubstitutor(), signature.isRaw(), signature.isInGenericContext(),
          signature.getParameterTypes(), signature.getTypeParameters());
  }

  /**
   * Returns the list of super method signatures for the specified signature.
   *
   * @return the super method signature list.
   * Note that the list may include signatures for which isSubsignature() check returns false, but erasures are equal 
   */
  @NotNull public abstract List<HierarchicalMethodSignature> getSuperSignatures();
}
