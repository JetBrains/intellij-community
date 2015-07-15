import com.google.common.collect.FluentIterable;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main20 {
  void get() {
    Stream<String> i = new ArrayList<String>().stream();
    if (i.map(String::isEmpty).findFirst().orElse(null)) {
      System.out.println(String.format("asd %s zxc", i.collect(Collectors.toList())));
    }
  }
}