import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;

public class TestSimpleAnnotationsDetection {
  @Blocking
  private static void testBlocking() {}

  @NonBlocking
  private static void nonBlocking() {
    <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">testBlocking</warning>();
  }

  private static void withoutAnnotation() {
    testBlocking();
  }
}