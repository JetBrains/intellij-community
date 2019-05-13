// "Create inner class 'ArrayList'" "true"
public class Test {
  public static void main() {
    Inner q = new Inner();
    q.new <caret>ArrayList();
  }

  static class Inner { }
}