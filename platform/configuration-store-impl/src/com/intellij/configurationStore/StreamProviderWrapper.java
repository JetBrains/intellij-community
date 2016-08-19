package com.intellij.configurationStore;

import com.intellij.openapi.components.RoamingType;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;

/**
 * @author Alexander Lobas
 */
public class StreamProviderWrapper implements StreamProvider {
  private StreamProvider myStreamProvider;

  @Nullable
  public static StreamProvider getOriginalProvider(@Nullable StreamProvider provider) {
    if (provider instanceof StreamProviderWrapper) {
      return ((StreamProviderWrapper)provider).myStreamProvider;
    }
    return null;
  }

  public void setStreamProvider(@Nullable StreamProvider streamProvider) {
    myStreamProvider = streamProvider;
  }

  @Override
  public boolean getEnabled() {
    return myStreamProvider != null && myStreamProvider.getEnabled();
  }

  @Override
  public boolean isApplicable(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
    return getEnabled() && myStreamProvider.isApplicable(fileSpec, roamingType);
  }

  @Nullable
  @Override
  public InputStream read(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
    return myStreamProvider.read(fileSpec, roamingType);
  }

  @Override
  public void processChildren(@NotNull String path,
                              @NotNull RoamingType roamingType,
                              @NotNull Function1<? super String, Boolean> filter,
                              @NotNull Function3<? super String, ? super InputStream, ? super Boolean, Boolean> processor) {
    myStreamProvider.processChildren(path, roamingType, filter, processor);
  }

  @Override
  public void write(@NotNull String fileSpec, @NotNull byte[] content, int size, @NotNull RoamingType roamingType) {
    myStreamProvider.write(fileSpec, content, size, roamingType);
  }

  @Override
  public void delete(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
    myStreamProvider.delete(fileSpec, roamingType);
  }
}
