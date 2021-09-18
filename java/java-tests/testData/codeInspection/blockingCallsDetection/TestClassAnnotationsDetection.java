import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NonBlocking;

public class TestClassAnnotationsDetection {
  @NonBlocking
  private static void nonBlocking(BlockingApiClass blockingApi, NonBlockingApiClass nonBlockingApi) {
    blockingApi.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">runBlocking</warning>();
    blockingApi.runNonBlocking();

    nonBlockingApi.<warning descr="Possibly blocking call in non-blocking context could lead to thread starvation">runBlocking</warning>();
    nonBlockingApi.runNonBlocking();
  }
}

@Blocking
class BlockingApiClass {
  public void runBlocking() { }

  @NonBlocking
  public void runNonBlocking() { }
}

@NonBlocking
class NonBlockingApiClass {
  @Blocking
  public void runBlocking() { }

  public void runNonBlocking() { }
}