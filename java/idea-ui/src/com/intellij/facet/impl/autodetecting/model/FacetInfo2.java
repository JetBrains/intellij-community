package com.intellij.facet.impl.autodetecting.model;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface FacetInfo2<M> {

  @NotNull
  String getFacetName();

  @NotNull
  FacetConfiguration getConfiguration();

  @NotNull
  FacetType<?,?> getFacetType();

  @Nullable
  FacetInfo2<M> getUnderlyingFacetInfo();

  @NotNull
  M getModule();
}
