public class Test {

  static class AAA {
    public void mmm(int i) {
      System.out.println(i + 10);
    }
  }

  static class BBB extends AAA {
    @Override
    public void mmm(int i) {
      System.out.println(i + 20);
    }
  }

  public static void main(String[] args) {
    new BBB().mmm(10);
    new AAA().mmm(10);
  }
}