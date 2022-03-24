// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.makeStatic;

import com.intellij.model.ModelBranch;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.util.VariableData;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class Settings {
  private final boolean myMakeClassParameter;
  private final String myClassParameterName;
  private final boolean myMakeFieldParameters;
  private final HashMap<PsiField,String> myFieldToNameMapping;
  private final ArrayList<FieldParameter> myFieldToNameList;
  private final boolean myReplaceUsages;
  private final boolean myDelegate;


  public static final class FieldParameter {
    public FieldParameter(PsiField field, String name, PsiType type) {
      this.field = field;
      this.name = name;
      this.type = type;
    }

    public final PsiField field;
    public final String name;
    public final PsiType type;
  }


  public Settings(boolean replaceUsages, @Nullable String classParameterName, VariableData @Nullable [] variableDatum) {
    this(replaceUsages, classParameterName, variableDatum, false);
  }

  public Settings(boolean replaceUsages,
                  @Nullable String classParameterName,
                  VariableData @Nullable [] variableDatum,
                  boolean delegate) {
    myReplaceUsages = replaceUsages;
    myDelegate = delegate;
    myMakeClassParameter = classParameterName != null;
    myClassParameterName = classParameterName;
    myMakeFieldParameters = variableDatum != null;
    myFieldToNameList = new ArrayList<>();
    if(myMakeFieldParameters) {
      myFieldToNameMapping = new HashMap<>();
      for (VariableData data : variableDatum) {
        if (data.passAsParameter) {
          myFieldToNameMapping.put((PsiField)data.variable, data.name);
          myFieldToNameList.add(new FieldParameter((PsiField)data.variable, data.name, data.type));
        }
      }
    }
    else {
      myFieldToNameMapping = null;
    }
  }

  public Settings(boolean replaceUsages,
                  String classParameterName,
                  PsiField @NotNull [] fields,
                  String[] names) {
    this(replaceUsages, classParameterName, fields, names, false);
  }

  private Settings(boolean replaceUsages,
                   String classParameterName,
                   PsiField @NotNull [] fields,
                   String[] names,
                   boolean delegate) {
    myReplaceUsages = replaceUsages;
    myMakeClassParameter = classParameterName != null;
    myClassParameterName = classParameterName;
    myMakeFieldParameters = fields.length > 0;
    myFieldToNameList = new ArrayList<>();
    if (myMakeFieldParameters) {
      myFieldToNameMapping = new HashMap<>();
      for (int i = 0; i < fields.length; i++) {
        final PsiField field = fields[i];
        final String name = names[i];
        myFieldToNameMapping.put(field, name);
        myFieldToNameList.add(new FieldParameter(field, name, field.getType()));
      }
    }
    else {
      myFieldToNameMapping = null;
    }
    myDelegate = delegate;
  }

  @NotNull Settings obtainBranchCopy(@NotNull ModelBranch branch) {
    if (myFieldToNameList.isEmpty()) return this; 
    return new Settings(myReplaceUsages, myClassParameterName,
                        ContainerUtil.map2Array(myFieldToNameList, PsiField.class, fp -> branch.obtainPsiCopy(fp.field)),
                        ContainerUtil.map2Array(myFieldToNameList, String.class, fp -> fp.name),
                        myDelegate);
  }

  public boolean isReplaceUsages() {
    return myReplaceUsages;
  }

  public boolean isMakeClassParameter() {
    return myMakeClassParameter;
  }

  public String getClassParameterName() {
    return myClassParameterName;
  }

  public boolean isMakeFieldParameters() {
    return myMakeFieldParameters;
  }

  public boolean isDelegate() {
    return myDelegate;
  }

  @Nullable
  public String getNameForField(PsiField field) {
    if (myFieldToNameMapping != null) {
      return myFieldToNameMapping.get(field);
    }
    return null;
  }

  public List<FieldParameter> getParameterOrderList() {
    return myFieldToNameList;
  }

  public boolean isChangeSignature() {
    return isMakeClassParameter() || isMakeFieldParameters();
  }

  public int getNewParametersNumber() {
    final int result = isMakeFieldParameters() ? myFieldToNameList.size() : 0;
    return result + (isMakeClassParameter() ? 1 : 0);
  }
}
