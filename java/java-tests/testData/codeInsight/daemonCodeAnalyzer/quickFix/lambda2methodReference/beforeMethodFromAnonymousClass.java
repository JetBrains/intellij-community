// "Replace lambda with method reference" "false"

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

class Test {
  
  public static void main(String[] args) {
    List<String> l = Arrays.asList("America", "Britain", "Australia", "Brazil", "Canada");
    System.out.println(l);
    System.out.println(uniquifyListByProperty(l, Function.identity()));
    System.out.println(uniquifyListByProperty(l, s -> s.charAt(0)));
  }

  static <T, P> List<T> uniquifyListByProperty(List<T> list, Function<T, P> propertyExtractor) {
    return list.stream()
      .map(item -> new Object() {
        @Override
        public boolean equals(Object o) {
          return propertyExtractor.apply(item).equals(
            propertyExtractor.apply(this.getClass().cast(o).item()));
        }

        @Override
        public int hashCode() {
          return propertyExtractor.apply(item).hashCode();
        }

        T item() {
          return item;
        }
      })
      .distinct()
      .map(o -> o.<caret>item())
      .collect(Collectors.toList());
  }
}
