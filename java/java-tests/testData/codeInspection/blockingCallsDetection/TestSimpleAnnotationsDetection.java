import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;

public class TestSimpleAnnotationsDetection {
  @Blocking
  private static void testBlocking() {}

  @NonBlocking
  private static void nonBlocking() {
    <warning descr="Inappropriate blocking method call">testBlocking</warning>();
  }

  private static void withoutAnnotation() {
    testBlocking();
  }
}