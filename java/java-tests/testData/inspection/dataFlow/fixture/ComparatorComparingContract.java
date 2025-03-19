import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ComparatorComparingContract {
  Comparator<MyObj> comparatorMR = Comparator.comparing(<warning descr="Function may return null, but it's not allowed here">MyObj::getName</warning>);
  Comparator<MyObj> comparatorLambda = Comparator.comparing(myObj -> <warning descr="Expression 'myObj.getName()' might evaluate to null but is returned by the method declared as @NotNull">myObj.getName()</warning>);

  interface MyObj {
    @Nullable String getName();
  }
}
