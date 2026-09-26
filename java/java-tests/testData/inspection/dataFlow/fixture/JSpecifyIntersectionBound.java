import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@NullMarked
class Test2 {
  <T extends @Nullable Object & CharSequence> T test(@Nullable T t) {
    return <warning descr="Expression 't' might evaluate to null but is returned by the method declared as @NullMarked">t</warning>;
  }

  <T extends Object & @Nullable CharSequence> T test2(@Nullable T t) {
    return <warning descr="Expression 't' might evaluate to null but is returned by the method declared as @NullMarked">t</warning>;
  }

  <T extends @NullnessUnspecified Object & @Nullable CharSequence> Object test3(T value) {
    return value; //expected: unspecified
  }
}
