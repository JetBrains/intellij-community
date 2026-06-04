// "Extract Set from comparison chain" "true-preview"

import java.util.Set;

class Orchard {
    private static final Set<String> FRUITS = Set.of("Apple", "Pear", "Banana");

    boolean check(String fruit) {
        /*1*/
        /*2*/
        /*3*/
        /* 4 */
        /*5*/
        return FRUITS.contains(fruit)/*6*/;
  }
}