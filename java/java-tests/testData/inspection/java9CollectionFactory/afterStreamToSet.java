// "Replace with 'Set.of' call" "true"
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  public static final Set<Class<?>> MY_SET = Set.of(String.class, int.class, Object.class);
}
