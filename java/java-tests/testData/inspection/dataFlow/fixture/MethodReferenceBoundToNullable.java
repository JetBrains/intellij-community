
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

// IDEA-186732
class MethodRef {

  public static <F, T> List<T> transform(
    List<F> fromList, Function<? super F, ? extends T> function) {
    return Collections.emptyList();
  }

  public static void useGuavaListsTransform_method_ref(List<@foo.Nullable String> list) {
    System.out.println(transform(list, s -> s.<warning descr="Method invocation 'length' may produce 'java.lang.NullPointerException'">length</warning>()));
    System.out.println(transform(list, <warning descr="Method reference invocation 'String::length' may produce 'java.lang.NullPointerException'">String::length</warning>));
  }
}
