package com.intellij.openapi.actionSystem;

/**
 * Interface for actions that require loading some extensions or services in order
 * to update themselves. Called from a background thread during pre-instantiation
 * of AnAction instances.
 *
 * @author yole
 */
public interface PreloadableAction {
  void preload();
}
