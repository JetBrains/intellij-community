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

      /*3*/
      /*1*/
      /*2*/
      /*5*/
      myNumber = Math.min(l +/*6*/1, myNumber/*4*/ - 2);

    System.out.println(myNumber);
  }
}