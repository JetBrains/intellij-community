// "Convert to record class" "true-preview"

package org.qw;

import java.util.List;
import java.util.Set;

class ArrayList<T> {

}

// Test for IDEA-310010
public record RecordWithEnum(Mode mode, String name, Nested nested, List<Integer> integers, Set<String> strings,
                             java.util.ArrayList<Double> doubles) {

    public enum Mode {
        Add, Sub
    }

    public static class Nested {
        int i;
    }
}
