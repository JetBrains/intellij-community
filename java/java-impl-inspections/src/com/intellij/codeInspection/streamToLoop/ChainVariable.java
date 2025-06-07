// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamToLoop;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * This class represents a variable which holds chain element. Its lifecycle is the following:
 * 1. Construction: fast, in case you don't need to perform a fix actually
 * 2. Preprocessing (addBestNameCandidate/addOtherNameCandidate/markFinal can be called).
 * 3. Register variable in {@code ChainContext}: actual variable name is assigned here
 * 4. Usage in code generation: getName()/getType()/isFinal() could be called.
 *
 * @author Tagir Valeev
 */
public class ChainVariable {
  private static final Logger LOG = Logger.getInstance(ChainVariable.class);

  public static final ChainVariable STUB = new ChainVariable(PsiTypes.voidType()) {
    @Override
    public void addBestNameCandidate(String candidate) {
    }

    @Override
    public void register(ChainContext context) {
    }

    @Override
    public String toString() {
      return "###STUB###";
    }
  };

  String myName;
  @NotNull PsiType myType;
  boolean myFinal;

  private Collection<String> myBestCandidates = new LinkedHashSet<>();
  private Collection<String> myOtherCandidates = new LinkedHashSet<>();

  public ChainVariable(@NotNull PsiType type) {
    myType = type;
  }

  public ChainVariable(@NotNull PsiType type, @NotNull String name) {
    myType = type;
    myName = name;
  }

  /**
   * Call if the resulting variable must be declared final (e.g. used in lambdas)
   */
  public void markFinal() {
    myFinal = true;
  }

  /**
   * Register best name candidate for this variable (like lambda argument which was explicitly present in the original code).
   *
   * @param candidate name candidate
   */
  public void addBestNameCandidate(String candidate) {
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
   * Register variable within {@link ChainContext}.
   * Must be called once after all name candidates are registered. Now variable gets an actual name.
   *
   * @param context context to use
   */
  public void register(ChainContext context) {
    LOG.assertTrue(myName == null);
    String[] fromType = JavaCodeStyleManager.getInstance(context.getProject())
      .suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, myType, true).names;
    List<String> variants = StreamEx.of(myBestCandidates).append(myOtherCandidates).append(fromType).distinct().toList();
    if (variants.isEmpty()) variants = List.of("val");
    myName = context.registerVarName(variants);
    myBestCandidates = myOtherCandidates = null;
  }

  public String getName() {
    return myName;
  }

  public @NotNull PsiType getType() {
    return myType;
  }

  public String getDeclaration() {
    return getType().getCanonicalText() + " " + getName();
  }

  public String getDeclaration(String initializer) {
    return getType().getCanonicalText() + " " + getName() + "=" + initializer + ";\n";
  }

  public boolean isFinal() {
    return myFinal;
  }

  public boolean isRegistered() {
    return myName != null;
  }

  @Override
  public String toString() {
    if (myName == null) {
      return "###(unregistered: " + myBestCandidates + "|" + myOtherCandidates + ")###";
    }
    return myName;
  }
}
