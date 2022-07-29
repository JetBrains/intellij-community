// "Replace 'switch' with 'if'" "true-preview"
public class One {
  void f1(String a) {
      if ("one".equals(a)) {
          System.out.println(1);
      }
      System.out.println("default");
  }
}