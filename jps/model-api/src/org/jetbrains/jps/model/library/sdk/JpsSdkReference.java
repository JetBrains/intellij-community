package org.jetbrains.jps.model.library.sdk;

import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.library.JpsTypedLibrary;

/**
 * @author nik
 */
public interface JpsSdkReference<P extends JpsElement> extends JpsElementReference<JpsTypedLibrary<JpsSdk<P>>> {
  String getSdkName();
}
