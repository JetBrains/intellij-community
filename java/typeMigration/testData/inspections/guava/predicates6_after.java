import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;

public class Test
{

  public void test() {
    Map<String, String> mapToFilter = ImmutableMap.of("A Key", "A Val", "B Key", "B Val");

    Maps.filterKeys(mapToFilter, Predicates.not(k -> k.startsWith("A")));
  }
}