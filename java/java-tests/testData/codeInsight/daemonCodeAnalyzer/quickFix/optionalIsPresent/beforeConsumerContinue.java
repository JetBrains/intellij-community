// "Replace Optional.isPresent() condition with functional style expression" "false"

import java.util.Optional;

public class Test {
  public static void main(String[] args) {
    for(String arg : args) {
      Optional<String> opt = Optional.of("xyz");
      if (opt.isPre<caret>sent()) {
        System.out.println(opt.get());
        System.out.println(opt.get());
        Runnable r = () -> {return;};
        for(int i=0; i<10; i++) {
          if(i == 3) continue;
          System.out.println(arg);
          if(i == 5)
            break;

        }
        System.out.println(opt.get());
        continue;
      }
    }
  }
}
