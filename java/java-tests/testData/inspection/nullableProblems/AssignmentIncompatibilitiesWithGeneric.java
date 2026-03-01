import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class TestListWrapperAssignment {


  ListArrayWrapper<@Nullable String> listByArrayWithNullField = ListArrayWrapper.createArray(new @Nullable String[] {"a", null, "c"});

  ListArrayWrapper<String> <warning descr="Assigning a class with nullable type arguments when a class with not-null type arguments is expected">listWithNullField</warning> = ListArrayWrapper.createVarargs("a", null, "c");

  public static void main(String[] args) {
    ListArrayWrapper.createVarargs("a", "c").getList().forEach(s -> System.out.println(s.toUpperCase(Locale.ROOT)));

    ListArrayWrapper<String> listArrayWrapper = ListArrayWrapper.createVarargs("a", "c");
    listArrayWrapper.getList().forEach(s -> System.out.println(s.toUpperCase(Locale.ROOT)));

    ListArrayWrapper<@Nullable String> listArrayWrapperNullable = ListArrayWrapper.createVarargs("a", "c");
    listArrayWrapperNullable.getList().forEach(s -> System.out.println(s.toUpperCase(Locale.ROOT)));

    ListArrayWrapper<String> <warning descr="Assigning a class with nullable type arguments when a class with not-null type arguments is expected">listWithNull</warning> = ListArrayWrapper.createVarargs("a", null, "c");

    ListArrayWrapper<@Nullable String> listByArrayWithNull = ListArrayWrapper.createArray(new @Nullable String[] {"a", null, "c"});
    ListArrayWrapper<String> varargs = ListArrayWrapper.createVarargs("1");
    ListArrayWrapper<@Nullable String> <warning descr="Assigning a class with not-null type arguments when a class with nullable type arguments is expected">listByArrayWithoutNull</warning> = varargs;
    ListArrayWrapper<String> <warning descr="Assigning a class with nullable type arguments when a class with not-null type arguments is expected">listByArrayWithNullCorrectInspection1</warning> = ListArrayWrapper.createArray(new @Nullable String[] {"a", null, "c"});
    ListArrayWrapper<@Nullable String> listByArrayWithNullCorrectInspection2 = ListArrayWrapper.createArray(new String[] {"a", null, "c"});
  }

  static class ListArrayWrapper<T extends @Nullable Object> {
    private final List<T> list;

    @SafeVarargs
    static <E extends @Nullable Object> ListArrayWrapper<E> createVarargs(E... elements) {
      return new ListArrayWrapper<>(new ArrayList<>(Arrays.asList(elements)));
    }

    static <E extends @Nullable Object> ListArrayWrapper<E> createArray(E[] array) {
      return new ListArrayWrapper<>(new ArrayList<>(Arrays.asList(array)));
    }

    private ListArrayWrapper(List<T> list) {
      this.list = list;
    }

    List<T> getList() {
      return list;
    }
  }
}