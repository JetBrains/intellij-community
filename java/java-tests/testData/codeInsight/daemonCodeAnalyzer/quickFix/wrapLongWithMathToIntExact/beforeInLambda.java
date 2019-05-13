// "Wrap using 'Math.toIntExact()'" "true"
import java.util.function.*;

public class Test {

    void m() {

      LongToIntFunction fn = x -> {
        if(x > 0) {
          return x<caret>*2;
        }
        return 0;
      };

    }

}
