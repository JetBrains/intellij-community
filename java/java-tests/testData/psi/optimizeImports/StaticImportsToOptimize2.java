import java.util.List;
import static java.util.Arrays.sort;
import static invalid.*;

class Foo {
  {
    sort(new long[0]);
  }
}
