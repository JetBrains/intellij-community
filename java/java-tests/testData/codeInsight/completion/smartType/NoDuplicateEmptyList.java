import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;

// IDEA-251394
public final class SomeUtils {

    private SomeUtils() {}

    public static List<Integer> getEventsFor(List<String> execution) {
        List<Long> statuses = new ArrayList<>();
        if (statuses.isEmpty()) {
            return <caret>
        }

        if (execution.size() > 1) {
            return Collections.emptyList();
        }
        return new ArrayList<>();
    }
}