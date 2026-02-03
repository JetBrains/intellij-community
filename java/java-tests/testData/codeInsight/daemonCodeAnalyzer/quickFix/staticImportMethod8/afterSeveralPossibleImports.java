// "Qualify static call..." "true"
import java.util.ArrayList;
import java.util.List;

public class Test {
  public static void main(String[] args) {
    System.out.println(B.produceSomething().isEmpty());
  }
}

class A {
  public static Object produceSomething() {
    return new ArrayList<>();
  }
}

class B {
  public static List<String> produceSomething() {
    return new ArrayList<>();
  }
}

class C {
  public static Integer produceSomething() {
    return 1;
  }
}