import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

class Test {

  {
    Map<String, ? extends Set<Integer>> myMap = null;

    myMap.entrySet().parallelStream()
      .map(it -> {
        final ArrayList<Integer> myList = newArrayList(it.getValue());
        return myList.size();
      })
      .forEach(System.out::println);
  }
  
  public static <E> ArrayList<E> newArrayList(Iterable<? extends E> elements) { return null; }
}