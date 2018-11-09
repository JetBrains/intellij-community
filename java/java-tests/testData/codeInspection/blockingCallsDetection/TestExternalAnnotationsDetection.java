import org.jetbrains.annotations.Blocking;

public class TestExternalAnnotationsDetection {
  @Blocking
  private static void testBlocking() {}

  private static void func() {
    <warning descr="Inappropriate blocking method call">testBlocking</warning>();
  }
}