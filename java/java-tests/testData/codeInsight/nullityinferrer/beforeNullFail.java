class Test {
  public class Infer1 {
    void perform(String s) {
      if (s == null) {
        throw new IllegalArgumentException();
      }
      System.out.println(s.length());
    }
  }
}