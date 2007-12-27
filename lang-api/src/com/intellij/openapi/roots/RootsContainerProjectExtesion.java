/*
 * User: anna
 * Date: 27-Dec-2007
 */
package com.intellij.openapi.roots;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class RootsContainerProjectExtesion extends ProjectExtension {
  @NotNull
  public abstract Set<String> getRootsToWatch();
}