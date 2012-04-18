import java.lang.Class;
import java.lang.String;

public class Test {
  public static void main(String[] args) {
    Test.class.getDeclaredConstructor(String.class).newInstance("Foo");
  }

  public Test(String param) {
    System.out.println("This is used!");
  }
}
