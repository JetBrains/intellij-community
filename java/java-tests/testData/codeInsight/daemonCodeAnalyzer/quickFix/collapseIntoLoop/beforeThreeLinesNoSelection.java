// "Collapse into loop" "true"
class X {
  void test(int[] data) {
    <caret>System.out.print("data["+0+"]");
    System.out.println("=");
    System.out.println(data[0]);
    System.out.print("data["+1+"]");
    System.out.println("=");
    System.out.println(data[1]);
    System.out.print("data["+2+"]");
    System.out.println("=");
    System.out.println(data[2]);
    System.out.print("data["+3+"]");
    System.out.println("=");
    System.out.println(data[3]);
    System.out.print("data["+4+"]");
    System.out.println("=");
    System.out.println(data[4]);
    System.out.print("data["+5+"]");
    System.out.println("=");
    System.out.println(data[5]);
  }
}