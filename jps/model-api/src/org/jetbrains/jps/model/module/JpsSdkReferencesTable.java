package org.jetbrains.jps.model.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;

/**
 * @author nik
 */
public interface JpsSdkReferencesTable extends JpsCompositeElement {
  @Nullable
  <P extends JpsElement>
  JpsSdkReference<P> getSdkReference(@NotNull JpsSdkType<P> type);

  <P extends JpsElement>
  void setSdkReference(@NotNull JpsSdkType<P> type, @NotNull JpsSdkReference<P> sdkReference);
}
