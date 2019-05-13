// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static void test(List<String> list) {
    while(true) {
        boolean b = true;
        for (String x : list) {
            if (x != null) {
                if (x.startsWith("x")) {
                    b = false;
                    break;
                }
            }
        }
        if (b) break;
        list = process(list);
    }
  }

  private static void test2(List<String> list) {
    while(list.size() > 2) {
        boolean b = true;
        for (String x : list) {
            if (x != null) {
                if (x.startsWith("x")) {
                    b = false;
                    break;
                }
            }
        }
        if (b) break;
        list = process(list);
    }
  }

  private static void test3(List<String> list) {
    while(true) {
        boolean b = false;
        for (String x : list) {
            if (x != null) {
                if (x.startsWith("x")) {
                    b = list.size() > 2;
                    break;
                }
            }
        }
        if (!(b)) break;
        list = process(list);
    }
  }

  private static void test4(List<String> list) {
    while(list.size() > 2) {
        long count = 0L;
        for (String x : list) {
            if (x != null) {
                if (x.startsWith("x")) {
                    count++;
                }
            }
        }
        if (!(count > 10 && list.size() < 10)) break;
        list = process(list);
    }
  }

  native List<String> process(List<String> list);
}