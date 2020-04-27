
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

// IDEA-186732
// In general nullability inference is unspecified and it's not clear whether `? super F` must be substituted via
// `@Nullable String`. Now it works because we propagate annotations through 
// com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeEqualityConstraint. However, normal Java 
// type inference should ignore annotations completely, so we might drop the support of this case in the future
// or define nullability inference in more strict way.
class MethodRef {

  public static <F, T> List<T> transform(
    List<F> fromList, Function<? super F, ? extends T> function) {
    return Collections.emptyList();
  }

  public static void useGuavaListsTransform_method_ref(List<@foo.Nullable String> list) {
    System.out.println(transform(list, s -> s.<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>()));
    System.out.println(transform(list, <warning descr="Method reference invocation 'String::length' may produce 'NullPointerException'">String::length</warning>));
  }
}
