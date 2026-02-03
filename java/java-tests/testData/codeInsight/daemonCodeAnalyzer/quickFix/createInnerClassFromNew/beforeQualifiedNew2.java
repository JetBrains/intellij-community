// "Create inner class 'ArrayList'" "true-preview"
public class Test {
  public static void main() {
    Inner q = new Inner();
    q.new <caret>ArrayList();
  }

  static class Inner { }
}