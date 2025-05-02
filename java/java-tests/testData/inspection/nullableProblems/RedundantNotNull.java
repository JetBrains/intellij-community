import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNullByDefault;
import java.util.List;

@NotNullByDefault
class FromDemo {
  <warning descr="Redundant nullability annotation in the scope of @NotNullByDefault">@NotNull</warning> String m(<warning descr="Redundant nullability annotation in the scope of @NotNullByDefault">@NotNull</warning> String s, @Nullable String s2) {
    return "";
  }
  
  <warning descr="Redundant nullability annotation in the scope of @NotNullByDefault">@NotNull</warning> String f;
  
  List<<warning descr="Redundant nullability annotation in the scope of @NotNullByDefault">@NotNull</warning> String> param(<warning descr="Redundant nullability annotation in the scope of @NotNullByDefault">@NotNull</warning> String <warning descr="Redundant nullability annotation in the scope of @NotNullByDefault">@NotNull</warning> [] <warning descr="Redundant nullability annotation in the scope of @NotNullByDefault">@NotNull</warning> [] a) {
    return List.of();
  }
}