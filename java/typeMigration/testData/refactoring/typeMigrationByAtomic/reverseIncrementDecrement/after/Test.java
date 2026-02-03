import java.util.concurrent.atomic.AtomicInteger;
class Test {
  int i;

  void foo() {
    i++;
    ++i;
    i--;
    --i;
    System.out.println(i++);
    System.out.println(--i);
  }
}