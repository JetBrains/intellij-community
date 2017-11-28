// "Replace with 'List.of' call" "true"
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  public static final List<Class<?>> MY_LIST = List.of(String.class, int.class, Object.class);
}
