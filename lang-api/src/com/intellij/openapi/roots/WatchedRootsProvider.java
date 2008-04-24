/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface WatchedRootsProvider {

  ExtensionPointName<WatchedRootsProvider> EP_NAME = new ExtensionPointName<WatchedRootsProvider>("com.intellij.roots.watchedRootsProvider");
  @NotNull
  Set<String> getRootsToWatch();
}