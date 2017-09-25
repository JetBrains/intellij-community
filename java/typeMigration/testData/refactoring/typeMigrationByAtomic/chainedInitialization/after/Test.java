import java.util.concurrent.atomic.AtomicInteger;

class Test {
  AtomicInteger a = new AtomicInteger();

  int b = a.get() + 10;
}