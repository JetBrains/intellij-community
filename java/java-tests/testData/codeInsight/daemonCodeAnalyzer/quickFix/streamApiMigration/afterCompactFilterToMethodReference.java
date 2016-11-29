// "Replace with forEach" "true"

import java.util.List;

public class A {
    private void withStream(List<String> stream, final List<String> activeFilters) {
        stream.stream().filter(String::isEmpty).forEach(activeFilters::add);
    }
}