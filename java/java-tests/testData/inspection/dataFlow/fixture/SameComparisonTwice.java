import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Doo {

  static final int TYPE_1 = 20;
  static final int TYPE_2 = 20;

  public static void failedSecondCondition(int type) {
    if(type != TYPE_1) {
      return;
    }

    if(<warning descr="Condition 'type != TYPE_2' is always 'false'">type != TYPE_2</warning>) {
      System.out.println();
    }
  }


}

class Doo2 {

  static final int TYPE_1 = 200;
  static final int TYPE_2 = 200;

  public static void failedSecondCondition(int type) {
    if(type != TYPE_1) {
      return;
    }

    if(<warning descr="Condition 'type != TYPE_2' is always 'false'">type != TYPE_2</warning>) {
      System.out.println();
    }
  }


}
