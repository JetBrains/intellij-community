// "Replace with collect" "true"

import java.util.List;
import java.util.stream.Collectors;

public class A {
    private void withStream(List<String> stream, final List<String> activeFilters) {
        activeFilters.addAll(stream.stream().filter(String::isEmpty).collect(Collectors.toList()));
    }
}