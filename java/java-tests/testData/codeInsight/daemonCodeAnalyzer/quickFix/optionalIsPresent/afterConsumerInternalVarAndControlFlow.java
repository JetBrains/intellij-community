// "Replace Optional.isPresent() condition with functional style expression" "INFORMATION"

import java.util.Optional;

public class Test {
  public static void main(String[] args) {
    for(String arg : args) {
      Optional<String> opt = Optional.of("xyz");
        opt.ifPresent(s -> {
            System.out.println(s);
            System.out.println(s);
            Runnable r = () -> {
                return;
            };
            for (int i = 0; i < 10; i++) {
                if (i == 3) continue;
                System.out.println(arg);
                if (i == 5)
                    break;

            }
            System.out.println(s);
            throw new RuntimeException();
        });
    }
  }
}
