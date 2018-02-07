package org.jetbrains.idea.maven.aether;

/**
 * @author Eugene Zhuravlev
 */
public interface ProgressConsumer {
  ProgressConsumer DEAF = new ProgressConsumer() {
    public void consume(String message) {
    }
  };

  void consume(String message);

  default boolean isCanceled() {
    return false;
  }
}
