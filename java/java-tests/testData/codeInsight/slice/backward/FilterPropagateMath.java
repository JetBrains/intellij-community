import java.util.Random;

class Test {
  public static void main(String[] args) {
    int[] data = new int[10];
    int i = <flown1><flown11>getIndex() % data.length;
    System.out.println(data[<caret>i]);
  }

  private static int abs(int <flown1111111>x) {
    return <flown1111>x < 0 ? <flown11111>-<flown111111>x : x;
  }

  private static int getIndex() {
    return <flown111>abs(<flown11111111>getCode());
  }

  private static int getCode() {
    switch (new Random().nextInt() % 4) {
      case 0: return 10;
      case 1: return Integer.MAX_VALUE;
      case 2: return -10;
      case 3: return <flown111111111>0x80000000;
      default: return 12345;
    }
  }
}