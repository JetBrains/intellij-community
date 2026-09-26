import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class NullableFieldWithBound<T extends @Nullable Object> {
  T <warning descr="Fields with non-null type bound must be initialized">field</warning>;
}