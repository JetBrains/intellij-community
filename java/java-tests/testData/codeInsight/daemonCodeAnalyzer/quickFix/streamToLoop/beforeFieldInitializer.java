// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
    static final List<String> STR = Stream.of("foo","bar","baz").ma<caret>p(String::toUpperCase).collect(Collectors.toList());

    final String field = STR.stream().filter(x -> !x.isEmpty()).collect(Collectors.joining(","));

    static {
        System.out.println("static initializer already exists");
    }

    final long count = Stream.of(1,2,3,4).filter(i -> i % 2 == 0).count();

    {
        System.out.println("initializer already exists");
    }

    final long x = 0, count2 = Stream.of(1,2,3,4).filter(i -> i % 2 == 0).count();

    final long count3 = Stream.of(1,2,3,4).filter(i -> i % 2 == 0).count(), y = 0;

    final long[] countArray = {Stream.of(1,2,3,4).filter(i -> i % 2 == 0).count()};
}