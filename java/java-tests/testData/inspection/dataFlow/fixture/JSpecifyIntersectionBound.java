import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Test2 {
  <T extends @Nullable Object & CharSequence> T test(@Nullable T t) {
    return <warning descr="Expression 't' might evaluate to null but is returned by the method declared as @NullMarked">t</warning>;
  }

  <T extends Object & @Nullable CharSequence> T test2(@Nullable T t) {
    return <warning descr="Expression 't' might evaluate to null but is returned by the method declared as @NullMarked">t</warning>;
  }
}