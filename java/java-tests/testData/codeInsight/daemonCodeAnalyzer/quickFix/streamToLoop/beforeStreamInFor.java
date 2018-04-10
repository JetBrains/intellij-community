// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
    String j = "foo";

    public void test(List<String> list) {
        for(long i = list.stream().filter(String::isEmpty).cou<caret>nt();
            i<10;
            i+=list.stream().filter(String::isEmpty).count()) {
            System.out.println(i);
        }

        for(long j = list.stream().filter(String::isEmpty).count();
            j<10;
            j+=list.stream().filter(String::isEmpty).count()) {
            System.out.println(j);
        }

        System.out.println(j);

        for(String s = list.stream().collect(Collectors.joining(",")); !s.isEmpty(); s = s.substring(1)) {
            System.out.println(s);
        }
    }

    public static void main(String[] args) {
        new Main().test(Arrays.asList("", "", "foo"));
    }
}