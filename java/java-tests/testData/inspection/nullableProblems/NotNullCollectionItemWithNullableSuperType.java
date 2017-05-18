import typeUse.*;
import java.util.*;

class MyList<T extends @NotNull Number> extends ArrayList<T> {}

class SubList extends MyList<<warning descr="Nullable type argument where non-null one is expected">@Nullable Integer</warning>> {
  MyList<<warning descr="Nullable type argument where non-null one is expected">@Nullable Integer</warning>> myList;
}

class MyNonNullGenericClass<T extends @NotNull Object> {
  public static void test() {
    MyNonNullGenericClass<<warning descr="Nullable type argument where non-null one is expected">@Nullable String</warning>> foo = new MyNonNullGenericClass<>();
  }
}

