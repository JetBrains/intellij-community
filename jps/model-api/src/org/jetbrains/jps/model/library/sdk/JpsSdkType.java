package org.jetbrains.jps.model.library.sdk;

import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.library.JpsLibraryType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;

/**
 * @author nik
 */
public abstract class JpsSdkType<P extends JpsElement> extends JpsLibraryType<JpsSdk<P>> {
  private final JpsElementChildRole<P> mySdkPropertiesRole = new JpsElementChildRole<P>();

  public final JpsElementChildRole<P> getSdkPropertiesRole() {
    return mySdkPropertiesRole;
  }
}
