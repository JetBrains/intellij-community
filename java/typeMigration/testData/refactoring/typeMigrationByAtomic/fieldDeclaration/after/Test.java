import java.util.concurrent.atomic.AtomicReference;

class Test {
  static final AtomicReference<String> FOO = new AtomicReference<String>("foo");
  AtomicReference<String> a = FOO;

  {
    System.out.println(a.get());
  }
}