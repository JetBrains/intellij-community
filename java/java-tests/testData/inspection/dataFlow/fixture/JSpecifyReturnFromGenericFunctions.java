import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

@NullMarked
class Main {

  public static void main(String[] args) {

    // (1) No warning expected: Expression 'getNullableObject()' might evaluate to null but is returned by the method declared as @NullMarked
    fNullableBound(() -> getNullableObject());

    // (2) No warning expected: Function may return null, but it's not allowed here
    fNullableBound(Main::getNullableObject);

    // No null warnings, as expected (this is the current workaround, to specify T explicitly)
    Main.<@Nullable Object>fNullableBound(() -> getNullableObject());
    Main.<@Nullable Object>fNullableBound(Main::getNullableObject);

    // Expected warning: Expression 'getNullableObject()' might evaluate to null but is returned by the method declared as @NullMarked
    fNonNullBound(() -> <warning descr="Expression 'getNullableObject()' might evaluate to null but is returned by the method declared as @NullMarked">getNullableObject()</warning>);

    // Expected warning: Function may return null, but it's not allowed here
    fNonNullBound(<warning descr="Function may return null, but it's not allowed here">Main::getNullableObject</warning>);

    // (3) NICE-TO-HAVE, it would be nice to have a warning that a @Nullable type for T is not allowed
    Main.<@Nullable Object>fNonNullBound(() -> <warning descr="Expression 'getNullableObject()' might evaluate to null but is returned by the method declared as @NullMarked">getNullableObject()</warning>);
    Main.<@Nullable Object>fNonNullBound(<warning descr="Function may return null, but it's not allowed here">Main::getNullableObject</warning>);

  }

  static <T extends @Nullable Object> T fNullableBound(Supplier<T> supplier){
    return supplier.get();
  }

  static <T> T fNonNullBound(Supplier<T> supplier){
    return supplier.get();
  }

  static @Nullable Object getNullableObject() {
    return null;
  }

}