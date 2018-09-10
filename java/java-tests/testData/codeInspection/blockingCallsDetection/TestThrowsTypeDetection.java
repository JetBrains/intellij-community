import org.jetbrains.annotations.NonBlocking;

public class TestThrowsTypeDetection {
  @NonBlocking
  private static void nonBlocking() {
    try {
      Thread.<warning descr="Inappropriate blocking method call">sleep</warning>(1L);
    } catch (InterruptedException ignored) {}
  }
}