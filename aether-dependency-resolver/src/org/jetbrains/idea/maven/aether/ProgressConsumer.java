package org.jetbrains.idea.maven.aether;

import org.jetbrains.annotations.Nls;

/**
 * @author Eugene Zhuravlev
 */
public interface ProgressConsumer {
  ProgressConsumer DEAF = new ProgressConsumer() {
    @Override
    public void consume(String message) {
    }
  };

  void consume(@Nls String message);

  default boolean isCanceled() {
    return false;
  }
}
