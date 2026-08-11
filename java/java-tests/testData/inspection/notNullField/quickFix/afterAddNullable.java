// "Annotate field 'field' as '@Nullable'" "true"
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class NullableFieldWithBound<T extends @Nullable Object> {
  @Nullable T field;
}