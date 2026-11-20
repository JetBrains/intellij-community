import java.util.*;
import java.util.function.Predicate;

class Test {
  static class AllPredicate<T> implements Predicate<T> {
    AllPredicate(List<Predicate<? super T>> predicates) {}

    @Override
    public boolean test(T t) {
      return true;
    }
  }

  static <X> Predicate<X> make(List<Predicate<? super X>> list) {
    return null;
  }

  @SafeVarargs
  static <T> Predicate<T> diamond(Predicate<? super T>... predicates) {
    return new AllPredicate<error descr="Cannot infer arguments"><></error>(Arrays.asList(predicates));
  }

  static <T> void diamondWithoutTarget(Predicate<? super T>[] predicates) {
    new AllPredicate<><error descr="'AllPredicate(java.util.List<java.util.function.Predicate<? super capture<? super T>>>)' in 'Test.AllPredicate' cannot be applied to '(java.util.List<java.util.function.Predicate<? super T>>)'">(Arrays.asList(predicates))</error>;
  }

  @SafeVarargs
  static <T> Predicate<T> explicitTypeArgument(Predicate<? super T>... predicates) {
    return new AllPredicate<T>(Arrays.asList(predicates));
  }

  static <T> Predicate<T> genericMethod(Predicate<? super T>[] predicates) {
    return make<error descr="'make(java.util.List<java.util.function.Predicate<? super T>>)' in 'Test' cannot be applied to '(java.util.List<java.util.function.Predicate<? super T>>)'">(Arrays.asList(predicates))</error>;
  }

  static <T> Predicate<T> explicitMethodTypeArgument(Predicate<? super T>[] predicates) {
    return Test.<T>make(Arrays.asList(predicates));
  }

  static <T> Predicate<T> copyConstructor(List<Predicate<? super T>> predicates) {
    return make<error descr="'make(java.util.List<java.util.function.Predicate<? super T>>)' in 'Test' cannot be applied to '(java.util.ArrayList<java.util.function.Predicate<? super T>>)'">(new ArrayList<>(predicates))</error>;
  }

  static <T> Predicate<T> capturedArgument(Predicate<? super T> predicate) {
    return make(Collections.singletonList(predicate));
  }
}
