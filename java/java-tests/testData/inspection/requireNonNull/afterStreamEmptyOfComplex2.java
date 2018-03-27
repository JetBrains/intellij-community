// "Replace condition with Stream.ofNullable" "true"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  private static final Map<String, String> meaningfulData
    = Collections.unmodifiableMap(new HashMap<>() {{
    put("IntelliJ", "IDEA");
    put("San Francisco", "California");
    put("Java", "One");
    put("Java 9", "Jigsaw");
  }});


  public static List<String> request(Set<String> keys) {
    System.out.println("IntelliService in module: " + Main.class.getModule());

    return keys.stream().flatMap(key -> {
      String val = meaningfulData.get(key);
        return Stream.ofNullable(val);
    }).collect(Collectors.toList());
  }

}
