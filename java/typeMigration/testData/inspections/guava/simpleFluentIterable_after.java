import com.google.common.base.Function;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class A {

  Function<String, String> myFunction = new Function<String, String>() {
    @Override
    public String apply(String input) {
      return input.trim();
    }
  };

  List<String> main(String[] args) {
    ArrayList<String> strings = new ArrayList<String>();
    Stream<String> it = strings.stream();
    it = it.map(input -> input.intern());
    it = it.map(String::trim);
    it = it.map(input -> {
        System.out.println("do some action on " + input);
        return input.substring(0, 10);
    });
    it = it.map(myFunction::apply);
    return it.collect(Collectors.toList());
  }

}