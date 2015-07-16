import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FluentIterableMain {

  Function<String, String> getFunction(int parameter) {
    return new Function<String, String>() {
      @Override
      public String apply(String input) {
        return null;
      }
    };
  }

  Iterable<String> m2() {
    Stream<String> i;
    i = new ArrayList<String>().stream().map(String::trim);

    i.map(null);

    String s = i.filter(null).findFirst().orElse(null);

    i = i.map(getFunction(10));

    i = i.map(new Function<String, String>() {
        @Override
        public String apply(String input) {
            return null;
        }
    });

    i.filter((Object.class)::isInstance);

    i = i.map(s11 -> s11.intern());
    i = i.map(String::intern);

    i = i.map(s11 -> {
        return s11.intern();
    });

    i.filter((String ssss) -> {
        System.out.println(ssss);
        return ssss.contains("asfdsf");
    }).findFirst().orElse("");

    m3(i.collect(Collectors.toList()));
    m4(i.map(String::trim).map(String::isEmpty).collect(Collectors.toList()));

    i.collect(Collectors.toList()).get(10);

    boolean empty = !i.findAny().isPresent();
    String last = i.reduce((previous, current) -> current).get();

    i.findFirst();
    i.forEach(s1 -> {

      System.out.println(s1);

    });

    i.skip(10);

    ArrayList<String> l = new ArrayList<>();
    i.forEach(l::add);
    System.out.println();
    System.out.println();
    i.anyMatch(e -> e != null && e.equals(123));
    i.forEach(System.out::println);
    System.out.println(i.allMatch(a -> a.isEmpty()));
    System.out.println(i.anyMatch(a -> a.isEmpty()));
    i.map(a -> a);
    i.map(a -> a);

    i = i.skip(10);

    i = i.filter(s10 -> s10.isEmpty());
    i = i.map(s11 -> s11.intern()).map(s12 -> String.valueOf(s12));

    System.out.println();

    i = i.map(String::trim);

    List<String> list = i.collect(Collectors.toList());
    String[] arr = i.toArray(String[]::new);

    String stringOptional = i.filter(String::isEmpty).findFirst().get();

    return i.collect(Collectors.toList());
  }

  void m3(Iterable i) {

  }

  void m4(Iterable i) {
  }

}