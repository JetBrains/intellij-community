package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;

/**
 * @author nik
 */
public interface JpsSdkReferencesTable extends JpsCompositeElement {
  @Nullable
  JpsLibraryReference getSdkReference(@NotNull JpsSdkType<?> type);

  void setSdkReference(@NotNull JpsSdkType<?> type, @NotNull JpsLibraryReference sdkReference);
}
