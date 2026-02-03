import java.util.ArrayList;

import static java.util.Arrays.asList;
import static java.util.Arrays.deepHashCode;
import static java.util.Arrays.sort;
import static java.util.Collections.sort;

class OnDemand {
  {
    sort(new Integer[0]);
    asList(new Integer[0]);
    deepHashCode(new Integer[0]);

    sort(new ArrayList<String>());
  }
}
