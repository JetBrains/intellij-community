import java.util.function.Supplier;

public class Test {

  public Test() {
    this(0);
  }

  public Test(int i) {}


  {
    Supplier<Test> sup = Test::ne<caret>w;
  }
}