public class Main {
  public void main() {
    try {
      System.out.print("abc" +
        "<caret>def");
    } catch (java.lang.Exception exception) {
      exception.printStackTrace();
    }
  }
}