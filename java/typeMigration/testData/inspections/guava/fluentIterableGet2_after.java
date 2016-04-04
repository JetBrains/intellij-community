import java.util.ArrayList;

public class A {
  static void m()  {
    String str = new ArrayList<String>().stream().skip(123).findFirst().get();
  }
}
