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
package com.intellij.pom;

/**
 * An abstract semantic entity defined in terms of some model (a filesystem target, a Spring bean target, etc.).
 *
 * <h3>Program Object Model (POM)</h3>
 * {@code PomTarget} was an attempt to untie code semantics from PSI.
 * The idea is that references could {@link com.intellij.psi.PsiReference#resolve() resolve} to a {@code PomTarget}
 * which doesn't necessarily have to be a {@link com.intellij.psi.PsiElement PsiElement} in some PSI tree.
 * <p>
 * {@code PomTarget} mimics as {@code PsiElement} ({@link PomTargetPsiElement}) to fit in older APIs.
 * <p>
 * The {@code PomTarget}'s accompanying PSI element can be retrieved via {@link com.intellij.pom.references.PomService#convertToPsi(PomTarget) PomService.convertToPsi}.
 * All references to this target should resolve to that PSI element.
 * <p>
 * The current attempt to untie the semantics from PSI is {@link com.intellij.model.Symbol Symbol}.
 * Prefer using it in new code.
 * <p>
 * IntelliJ Program Object Model must not be confused with <a href="https://maven.apache.org/pom.html">Maven's Project Object Model</a>.
 *
 * @see PomDeclarationSearcher
 */
public interface PomTarget extends Navigatable {
  PomTarget[] EMPTY_ARRAY = new PomTarget[0];

  boolean isValid();
}
