// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        List<String> list = Arrays.asList("a", "b", "c", "d");
        for (String o : list) {
            if (!o.isEmpty()) {
                System.out.println("Peek: " + o);
                System.out.println("Peek2: " + o);
                String n = "";
                System.out.println(o);
                if (o.equals("c")) continue;
                System.out.println(o + "!!!" + n);
                new Runnable() {
                    String e = "x";

                    public void run() {
                        System.out.println(e);
                        for (int i = 0; i < 10; i++) {
                            if (e.length() == i) return;
                        }
                    }
                }.run();
            }
        }

        for (String s : list) {
            if ("b".equals(s)) {
                System.out.println("Found:");
                System.out.println(s);
                break;
            }
        }
        Optional<String> found = Optional.empty();
        for (String qq : list) {
            if ("b".equals(qq)) {
                found = Optional.of(qq);
                break;
            }
        }
        found.ifPresent(str -> {
            System.out.println("Found:");
            if(str.isEmpty()) return; // return inside ifPresent is not supported
            System.out.println(str);
        });

        StringBuilder res = new StringBuilder();
        for (String str : list) {
            if (str != null) {
                str = "[" + str + "]";
                res.append(str);
            }
        }
        System.out.println(res);

        long count = 0L;
        for (String n : list) {
            if (!"a".equals(n)) {
                for (int i = 0; i < 3; i++) {
                    System.out.println("In flatmap idx: " + i);
                    System.out.println("In flatmap: " + n);
                    count++;
                }
            }
        }
        System.out.println(count);
    }

    void test(List<String> list) {
        for (String x : list) {
            if (x.isEmpty()) continue;
            System.out.println(x);
        }
    }
}