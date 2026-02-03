import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.util.Comparator.*;

public class ComparatorComparingContract {
  Comparator<MyObj> comparatorMR = Comparator.comparing(<warning descr="Function may return null, but it's not allowed here">MyObj::getName</warning>);
  Comparator<MyObj> comparatorLambda = Comparator.comparing(myObj -> <warning descr="Expression 'myObj.getName()' might evaluate to null but is returned by the method declared as @NotNull">myObj.getName()</warning>);

  interface MyObj {
    @Nullable String getName();
  }

  public static List<Integer> nullComparator() {
    final List<@Nullable Integer> list = new ArrayList<>();
    list.add(null);
    list.sort(nullsFirst(Integer::compareTo));
    list.sort(nullsLast(Integer::compareTo));
    return list;
  }
}
