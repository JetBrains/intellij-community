package staticImports;

import java.util.Arrays;
import static java.util.Collections.*;
import java.util.Collections;
import static java.util.Arrays.*;
import static java.util.Collections.sort;
import static java.util.Arrays.sort;

public class StaticImports {
  {
    sort(new List());
    sort(new List<String>());
    sort(new int[256]);
    Integer[] v = new Integer[256];
    sort(v)
    sort(asList(v));
  }
}

