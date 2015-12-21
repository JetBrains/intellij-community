import java.util.ArrayList;

class A {
  void m1() {
    ArrayList<String> strings = new ArrayList<String>();
    String str = strings.stream().map(s -> s + s).limit(10).filter(s -> s.isEmpty()).findFirst().orElse(null);
    System.out.println("s: " + str);
  }
}