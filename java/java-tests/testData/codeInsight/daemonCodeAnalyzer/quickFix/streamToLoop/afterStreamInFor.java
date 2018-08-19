// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
    String j = "foo";

    public void test(List<String> list) {
        long i = 0L;
        for (String s : list) {
            if (s.isEmpty()) {
                i++;
            }
        }
        for(;
            i<10;
            i+=list.stream().filter(String::isEmpty).count()) {
            System.out.println(i);
        }

        {
            long j = 0L;
            for (String s : list) {
                if (s.isEmpty()) {
                    j++;
                }
            }
            for(;
                j<10;
                j+=list.stream().filter(String::isEmpty).count()) {
                System.out.println(j);
            }
        }

        System.out.println(j);

        StringJoiner joiner = new StringJoiner(",");
        for (String s1 : list) {
            joiner.add(s1);
        }
        for(String s = joiner.toString(); !s.isEmpty(); s = s.substring(1)) {
            System.out.println(s);
        }
    }

    public static void main(String[] args) {
        new Main().test(Arrays.asList("", "", "foo"));
    }
}