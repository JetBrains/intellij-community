
import java.util.Comparator;
import static java.util.Comparator.*;

enum TestEnum {
  E(naturalOrder());
  
  TestEnum(Comparator<String> c) {}
}