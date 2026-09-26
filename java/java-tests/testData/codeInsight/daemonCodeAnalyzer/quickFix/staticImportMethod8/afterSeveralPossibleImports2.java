// "Qualify static call..." "true"
import java.util.ArrayList;
import java.util.List;

public class Test {
  public static void main(String[] args) {
    System.out.println(B.produceSomething(1).isEmpty());
  }
}

class A {
  public static Object produceSomething(Integer i) {
    return new ArrayList<>();
  }
}

class B {
  public static List<String> produceSomething(Integer i) {
    return new ArrayList<>();
  }
  public static Object produceSomething(Integer i) {
    return new ArrayList<>();
  }
}

class C {
  public static Integer produceSomething(Integer i) {
    return 1;
  }
}