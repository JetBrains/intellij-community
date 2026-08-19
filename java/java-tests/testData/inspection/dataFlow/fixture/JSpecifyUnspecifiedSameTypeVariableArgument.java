import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@NullMarked
class JSpecifyUnspecifiedSameTypeVariableArgument<T extends @Nullable Object> {
  void useT(T t) {}


  void caller(@NullnessUnspecified T tUnspec) {
    useT(tUnspec);
  }
}
