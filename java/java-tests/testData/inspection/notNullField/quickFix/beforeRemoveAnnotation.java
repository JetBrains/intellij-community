// "Remove not-null annotation" "true"
import org.jetbrains.annotations.NotNull;

class X {
  @<caret>NotNull String x;
}