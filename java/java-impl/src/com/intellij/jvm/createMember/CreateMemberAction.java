/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.jvm.createMember;

import com.intellij.jvm.createMember.java.CreateJavaMethodFactory;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * This class serves as HOW in the API.
 * It concrete action of rendering the member.
 * Instances are created by implementations of {@link CreateJvmMemberFactory}.
 * <p>
 * Example.
 * <p>
 * Given unresolved Java reference foo.getBar().<br/>
 * Java site creates a method request.<br/>
 * <p>
 * If Foo class is a Java class, then Java {@link CreateJavaMethodFactory implementation}
 * would generate single action to render such method.
 * <p>
 * However this is not the case with Groovy.
 * In Groovy there are two possibilities to generate such method: explicit method or a property.<br/>
 * If Foo class is a Groovy class, then Groovy implementation would generate two actions which will be presented to the user.
 */
public interface CreateMemberAction {

  @Nullable
  default Icon getIcon() { return null; }

  @NotNull
  String getTitle();

  @NotNull
  PsiElement renderMember() throws IncorrectOperationException;
}
