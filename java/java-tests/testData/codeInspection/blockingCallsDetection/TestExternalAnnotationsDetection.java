import org.jetbrains.annotations.Blocking;

public class TestExternalAnnotationsDetection {
  @Blocking
  private static void testBlocking() {}

  private static void func() {
    <warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">testBlocking</warning>();
  }
}