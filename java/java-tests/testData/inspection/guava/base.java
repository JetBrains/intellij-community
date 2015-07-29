import com.google.common.collect.FluentIterable;

import java.util.ArrayList;
import java.util.List;

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
    FluentIte<caret>rable<String> i;
    i = FluentIterable.from(new ArrayList<String>()).transform(String::trim);

    i.transform(null);

    String s = i.firstMatch(null).orNull();

    i = i.transform(getFunction(10));

    i = i.transform(new Function<String, String>() {
      @Override
      public String apply(String input) {
        return null;
      }
    });

    i.filter(Object.class);

    i = i.transform(s11 -> s11.intern());
    i = i.transform(String::intern);

    i = i.transform(s11 -> {
      return s11.intern();
    });

    i.firstMatch((String ssss) -> {
      System.out.println(ssss);
      return ssss.contains("asfdsf");
    }).or("");

    m3(i);
    m4(i.transform(String::trim).transform(String::isEmpty));

    i.toList().get(10);

    boolean empty = i.isEmpty();
    String last = i.last().get();

    i.first();
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
    i.transform(a -> a);
    i.transform(a -> a);

    i = i.skip(10);

    i = i.filter(s10 -> s10.isEmpty());
    i = i.transform(s11 -> s11.intern()).transform(s12 -> String.valueOf(s12));

    System.out.println();

    i = i.transform(String::trim);

    List<String> list = i.toList();
    String[] arr = i.toArray(String.class);

    String stringOptional = i.filter(String::isEmpty).first().get();

    return i;
  }

  void m3(Iterable i) {

  }

  void m4(Iterable i) {
  }

}