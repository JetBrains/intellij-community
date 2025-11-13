// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
    static final List<String> STR;

    final String field;

    static {
        List<String> list = new ArrayList<>();
        for (String s : Arrays.asList("foo", "bar", "baz")) {
            String upperCase = s.toUpperCase();
            list.add(upperCase);
        }
        STR = list;
        System.out.println("static initializer already exists");
    }

    final long count;

    {
        StringJoiner joiner = new StringJoiner(",");
        for (String s : STR) {
            if (!s.isEmpty()) {
                joiner.add(s);
            }
        }
        field = joiner.toString();
        long result = 0L;
        for (Integer i : Arrays.asList(1, 2, 3, 4)) {
            if (i % 2 == 0) {
                result++;
            }
        }
        count = result;
        System.out.println("initializer already exists");
    }

    final long x = 0;
    final long count2;

    final long count3;
    final long y = 0;

    final long[] countArray;

    {
        long result1 = 0L;
        for (Integer i1 : Arrays.asList(1, 2, 3, 4)) {
            if (i1 % 2 == 0) {
                result1++;
            }
        }
        count2 = result1;
        long count1 = 0L;
        for (Integer integer : Arrays.asList(1, 2, 3, 4)) {
            if (integer % 2 == 0) {
                count1++;
            }
        }
        count3 = count1;
        long result = 0L;
        for (Integer i : Arrays.asList(1, 2, 3, 4)) {
            if (i % 2 == 0) {
                result++;
            }
        }
        countArray = new long[]{result};
    }
}