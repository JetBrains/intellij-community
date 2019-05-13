import com.intellij.util.containers.HashSet;

import java.util.Set;
import java.util.HashSet;

public class Main {


  public static void main(String[] args) {
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            Set<String> test = new HashSet<String>();
            if (test.contains("AA")) {
              System.out.println("AAAA!");
            }
        }
    };

    runnable.run();
  }

}