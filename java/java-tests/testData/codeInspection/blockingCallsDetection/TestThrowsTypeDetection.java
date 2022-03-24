import org.jetbrains.annotations.NonBlocking;

public class TestThrowsTypeDetection {
  @NonBlocking
  private static void nonBlocking() {
    try {
      Thread.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">sleep</warning>(1L);
    } catch (InterruptedException ignored) {}
  }
}