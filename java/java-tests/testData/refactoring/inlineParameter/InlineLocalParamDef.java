public class ExpData {
  void foo(String s) {
    s = "";
    System.out.println(<caret>s.substring(2));
  }
}
