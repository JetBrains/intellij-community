import static java.util.Arrays.*;
import static java.util.Collections.sort;

class C {
  {
    int[] array = null;
    <caret>sort(array);
  }
}