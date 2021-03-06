import java.util.concurrent.atomic.AtomicInteger;

// "Convert to atomic" "true"
class Test {
  {
    int a = 42;
    AtomicInteger i = new AtomicInteger(-a);
  }
}