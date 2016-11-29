import java.util.concurrent.atomic.AtomicInteger;

class Test {
  AtomicInteger a = new AtomicInteger(0);

  int b = a.get() + 10;
}