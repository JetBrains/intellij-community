// "Replace with 'Math.min()' call" "true"
import java.util.Random;

class VariableValueUsed {
  private static final Random rnd = new Random();

  private static int test() {
    return rnd.nextInt();
  }

  public static void main(String[] args) {
    int l = 2;
    int myNumber = test();

    <caret>if (l+/*6*/1 <=/*3*/ myNumber/*4*/ - 2) {
      myNumber = l/*1*/+1;
    } else {
      myNumber = /*2*/ myNumber /*5*/- 2;
    }

    System.out.println(myNumber);
  }
}