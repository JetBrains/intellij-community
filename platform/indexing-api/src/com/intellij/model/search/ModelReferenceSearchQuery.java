// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.ModelReference;
import com.intellij.util.AbstractQuery;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class ModelReferenceSearchQuery extends AbstractQuery<ModelReference> {

  private final ModelReferenceSearchParameters myParameters;
  private final Query<ModelReference> myBaseQuery;

  public ModelReferenceSearchQuery(@NotNull ModelReferenceSearchParameters parameters, @NotNull Query<ModelReference> baseQuery) {
    myParameters = parameters;
    myBaseQuery = baseQuery;
  }

  @Override
  protected boolean processResults(@NotNull Processor<ModelReference> consumer) {
    return myBaseQuery.forEach(consumer) &&
           ModelSearchHelper.getInstance(myParameters.getProject()).runParameters(myParameters, consumer);
  }

  @Contract(pure = true)
  @NotNull
  public ModelReferenceSearchParameters getParameters() {
    return myParameters;
  }

  @Contract(pure = true)
  @NotNull
  public Query<ModelReference> getBaseQuery() {
    return myBaseQuery;
  }
}
