
import java.util.List;

class Temp {
  {
    new Runnable() {
      List<String> result = new <caret>
    }.run();
  }

}