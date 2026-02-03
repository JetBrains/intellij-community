
import java.util.Comparator;

class Test {
  private final int x;

  public Test(int x) {
    this.x = x;
  }

  public static final Comparator<Test> comparator = (o1, o2) -> {
    if (o1.x == o2.x) {
    }
    <error descr="'Test.this' cannot be referenced from a static context">this</error>.x != o2.x;
    return 0;
  };
}