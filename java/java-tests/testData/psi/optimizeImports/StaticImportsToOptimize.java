import java.util.List;
import static java.util.Arrays.sort;
import static java.util.Arrays.asList;
import static java.util.Arrays.invalid;
import static invalid.*;

class Foo {
  {
    sort(new Integer[0]);
  }
}
