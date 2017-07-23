// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        List<String> list = Arrays.asList("a", "b", "c", "d");
        list.stream().filter(n -> !n.isEmpty()).peek(o -> {
            System.out.println("Peek: "+o);
            System.out.println("Peek2: "+o);
        }).for<caret>Each(e -> {
            String n = "";
            System.out.println(e);
            if (e.equals("c")) return;
            System.out.println(e + "!!!" + n);
            new Runnable() {String e = "x"; public void run() {
                System.out.println(e);
                for(int i=0; i<10; i++) {
                  if(e.length() == i) return;
                }
            }}.run();
        });

        list.stream().filter("b"::equals).findFirst().ifPresent(str -> {
            System.out.println("Found:");
            System.out.println(str);
        });
        list.stream().filter(qq -> "b".equals(qq)).findFirst().ifPresent(str -> {
            System.out.println("Found:");
            if(str.isEmpty()) return; // return inside ifPresent is not supported
            System.out.println(str);
        });

        StringBuilder res = list.stream().filter(str -> str != null).collect(StringBuilder::new, (sb, s) -> {
            s = "[" + s + "]";
            sb.append(s);
        }, StringBuilder::append);
        System.out.println(res);

        System.out.println(list.stream().filter(n -> !"a".equals(n)).flatMapToInt(l -> IntStream.range(0, 3).peek(n -> {
            System.out.println("In flatmap idx: "+n);
            System.out.println("In flatmap: "+l);
        })).count());
    }

    void test(List<String> list) {
        list.forEach(x -> {
            if(x.isEmpty()) return;
            System.out.println(x);
        });
    }
}