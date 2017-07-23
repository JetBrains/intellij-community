import typeUse.*;
import java.util.*;
import java.util.function.*;

class JC {

  void testList() {
    List<@Nullable String> nullableList = new ArrayList<>();
    print(<warning descr="Assigning a collection of nullable elements into a collection of non-null elements">nullableList</warning>);

    List<@NotNull String> <warning descr="Assigning a collection of nullable elements into a collection of non-null elements">list2</warning> = nullableList;

    List<@NotNull String> list3;
    list2 <warning descr="Assigning a collection of nullable elements into a collection of non-null elements">=</warning> nullableList;

    List<? super @NotNull String> list4 = nullableList;
    List<? extends @NotNull String> <warning descr="Assigning a collection of nullable elements into a collection of non-null elements">list5</warning> = nullableList;
  }
  
  private static void print(List<@NotNull String> list) {
    for (String s : list) {
      System.out.println(s.length());
    }
  }

  List<@Nullable String> getNullableList() {return new ArrayList<>();}

  List<@NotNull String> testReturnValue() {
    List<@Nullable String> list = new ArrayList<>();

    Supplier<List<@NotNull String>> supplier = () -> <warning descr="Assigning a collection of nullable elements into a collection of non-null elements">list</warning>;
    Supplier<List<@NotNull String>> supplierRef = <warning descr="Assigning a collection of nullable elements into a collection of non-null elements">this::getNullableList</warning>;

    Supplier<List<@NotNull String>> supplier3 = () -> { return <warning descr="Assigning a collection of nullable elements into a collection of non-null elements">list</warning>;};

    return <warning descr="Assigning a collection of nullable elements into a collection of non-null elements">list</warning>;
  }
  
}

class Test<T extends @Nullable CharSequence> {
  public void test() {
    List<T> nullableList = new ArrayList<>();
    List<? extends @NotNull CharSequence> <warning descr="Assigning a collection of nullable elements into a collection of non-null elements">list2</warning> = nullableList;
  }
}