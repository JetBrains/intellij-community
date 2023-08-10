// "Replace 'switch' with 'if'" "true-preview"
public class One {
  void f1(String a) {
      if (a.equals("one")) {
          System.out.println(1);
      }
      System.out.println("default");
  }
}