import typeUse.*;
import java.util.*;

class JC {

  public static void main(String[] args) {
    List<@Nullable String> list = new ArrayList<>();
    print(<warning descr="Assigning a collection of nullable elements into a collection of non-null elements">list</warning>);

    List<@NotNull String> <warning descr="Assigning a collection of nullable elements into a collection of non-null elements">list2</warning> = list;

    List<@NotNull String> list3;
    list2 <warning descr="Assigning a collection of nullable elements into a collection of non-null elements">=</warning> list;
  }

  private static void print(List<@NotNull String> list) {
    for (String s : list) {
      System.out.println(s.length());
    }
  }
}