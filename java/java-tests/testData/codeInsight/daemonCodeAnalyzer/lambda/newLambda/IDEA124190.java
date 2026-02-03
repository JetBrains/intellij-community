import java.util.*;
import java.util.stream.Stream;

class Test {

  void foo(final Stream<String> stream){
    stream.collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    stream.collect(ArrayList<String>::new, ArrayList::add, ArrayList::addAll);
    stream.collect(ArrayList::new, Collection::add, Collection::addAll);
  }

}
