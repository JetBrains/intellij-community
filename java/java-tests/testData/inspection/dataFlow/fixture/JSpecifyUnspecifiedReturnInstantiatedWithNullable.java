import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@NullMarked
abstract class JSpecifyUnspecifiedReturnInstantiatedWithNullable {
  Object plainBound(NullableBounded<? extends Lib> x) {
    return unspec(x.get());
  }

  Object unspecBound(NullableBounded<? extends @NullnessUnspecified Lib> x) {
    return unspec(x.get());
  }

  Object nullableBound(NullableBounded<? extends @Nullable Lib> x) {
    return <warning descr="Expression 'unspec(x.get())' might evaluate to null but is returned by the method declared as @NullMarked">unspec(x.get())</warning>;
  }

  interface NullableBounded<T extends @Nullable Object> {
    T get();
  }

  interface Lib {}

  abstract <T extends @Nullable Object> @NullnessUnspecified T unspec(T input);
}
