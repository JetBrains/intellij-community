// "Replace with forEach" "true"

import java.util.List;

public class A {
    private void withStream(List<String> stream, final List<String> activeFilters) {
        for (String filter : st<caret>ream)
            if (filter.isEmpty()) {
              activeFilters.add(filter);
            }
    }
}