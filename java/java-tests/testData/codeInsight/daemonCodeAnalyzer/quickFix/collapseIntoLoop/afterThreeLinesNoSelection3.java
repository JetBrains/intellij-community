// "Collapse into loop" "true"
class X {
  void test(int[] data) {
      for (int i = 0; i < 6; i++) {
          System.out.print("data["+ i +"]");
          System.out.println("=");
          System.out.println(data[i]);
      }
      System.out.print("data["+6+"]");
  }
}