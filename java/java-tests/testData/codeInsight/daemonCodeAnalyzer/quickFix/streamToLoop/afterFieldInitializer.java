// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
    static final List<String> STR;

    static {
        List<String> list = new ArrayList<>();
        for (String s : Arrays.asList("foo", "bar", "baz")) {
            String toUpperCase = s.toUpperCase();
            list.add(toUpperCase);
        }
        STR = list;
    }

    final String field;

    {
        StringJoiner joiner = new StringJoiner(",");
        for (String s : STR) {
            if (!s.isEmpty()) {
                joiner.add(s);
            }
        }
        field = joiner.toString();
    }

    static {
        System.out.println("static initializer already exists");
    }

    final long count;

    {
        long result = 0L;
        for (Integer i : Arrays.asList(1, 2, 3, 4)) {
            if (i % 2 == 0) {
                result++;
            }
        }
        count = result;
        System.out.println("initializer already exists");
    }

    final long x = 0, count2 = Stream.of(1,2,3,4).filter(i -> i % 2 == 0).count();

    final long count3 = Stream.of(1,2,3,4).filter(i -> i % 2 == 0).count(), y = 0;

    final long[] countArray;

    {
        long result = 0L;
        for (Integer i : Arrays.asList(1, 2, 3, 4)) {
            if (i % 2 == 0) {
                result++;
            }
        }
        countArray = new long[]{result};
    }
}