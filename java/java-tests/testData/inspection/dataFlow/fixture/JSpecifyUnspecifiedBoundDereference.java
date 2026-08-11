import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

@NullMarked
class JSpecifyUnspecifiedBoundDereference<P extends @Nullable Object, T extends @NullnessUnspecified P> {
  void throughUnspecifiedBound(T t) {
    t.<warning descr="Method invocation 'toString' may produce 'NullPointerException'">toString</warning>();
  }
}
