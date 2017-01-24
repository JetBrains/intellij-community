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
package com.intellij.codeInspection.streamToLoop;

import com.intellij.codeInspection.streamToLoop.StreamToLoopInspection.StreamToLoopReplacementContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * This class represents a variable which holds stream element. Its lifecycle is the following:
 * 1. Construction: fast, in case you don't need to perform a fix actually
 * 2. Gather name candidates (addBestNameCandidate/addOtherNameCandidate can be called).
 * 3. Register variable in {@code StreamToLoopReplacementContext}: actual variable name is assigned here
 * 4. Usage in code generation: getName()/getType() could be called.
 *
 * @author Tagir Valeev
 */
class StreamVariable {
  private static final Logger LOG = Logger.getInstance(StreamVariable.class);

  static StreamVariable STUB = new StreamVariable("") {
    @Override
    public void addBestNameCandidate(String candidate) {
    }

    @Override
    void register(StreamToLoopReplacementContext context) {
    }

    @Override
    public String toString() {
      return "###STUB###";
    }
  };

  String myName;
  @NotNull String myType;

  private Collection<String> myBestCandidates = new LinkedHashSet<>();
  private Collection<String> myOtherCandidates = new LinkedHashSet<>();

  StreamVariable(@NotNull String type) {
    myType = type;
  }

  StreamVariable(@NotNull String type, @NotNull String name) {
    myType = type;
    myName = name;
  }

  /**
   * Register best name candidate for this variable (like lambda argument which was explicitly present in the original code).
   *
   * @param candidate name candidate
   */
  void addBestNameCandidate(String candidate) {
    myBestCandidates.add(candidate);
  }

  /**
   * Register normal name candidate for this variable (for example, derived using unpluralize from collection name, etc.)
   *
   * @param candidate name candidate
   */
  void addOtherNameCandidate(String candidate) {
    myOtherCandidates.add(candidate);
  }

  /**
   * Register variable within {@link StreamToLoopReplacementContext}.
   * Must be called once after all name candidates are registered. Now variable gets an actual name.
   *
   * @param context context to use
   */
  void register(StreamToLoopReplacementContext context) {
    LOG.assertTrue(myName == null);
    String[] fromType = JavaCodeStyleManager.getInstance(context.getProject())
      .suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, context.createType(myType), true).names;
    List<String> variants = StreamEx.of(myBestCandidates).append(myOtherCandidates).append(fromType).distinct().toList();
    if (variants.isEmpty()) variants.add("val");
    myName = context.registerVarName(variants);
    myBestCandidates = myOtherCandidates = null;
  }

  String getName() {
    LOG.assertTrue(myName != null);
    return myName;
  }

  @NotNull
  String getType() {
    return myType;
  }

  String getDeclaration() {
    return getType() + " " + getName();
  }

  String getDeclaration(String initializer) {
    return getType() + " " + getName() + "=" + initializer + ";\n";
  }

  @Override
  public String toString() {
    if (myName == null) {
      return "###(unregistered: " + myBestCandidates + "|" + myOtherCandidates + ")###";
    }
    return myName;
  }
}
