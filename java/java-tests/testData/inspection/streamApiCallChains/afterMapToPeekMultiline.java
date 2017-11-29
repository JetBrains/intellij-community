// "Replace with 'peek'" "true"

import java.util.List;

public class Main {
    void test(List<String> list) {
        long count = list.stream()
                .peek(e -> {
                    if(e.isEmpty()) {
                      System.out.println("Empty line passed!");
                      throw new IllegalArgumentException();
                    }
                    // hello
                    /* in return */
                })
                .count();
        System.out.println(count);
    }
}
