package com.intellij.openapi.util;

import java.util.EventListener;

/**
 * @author Gregory.Shrago
 */
public interface ModificationTrackerListener<T extends ModificationTracker> extends EventListener {
  void modificationCountChanged(T modificationTracker);
}
