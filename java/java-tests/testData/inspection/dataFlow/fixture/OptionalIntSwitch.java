import java.util.OptionalInt;
import java.util.Random;

class OptionalIntSwitch {
  // IDEA-240631
  public static void main(String[] args) {
    OptionalInt opt = OptionalInt.of(new Random().nextInt() % 2);

    switch (opt.getAsInt()) {
      case 0:
        System.out.println("zero");
        break;
      case 1:
        System.out.println("one");
        break;
      case <warning descr="Switch label '2' is unreachable">2</warning>:
        System.out.println("two");
        break;
    }
  }
}