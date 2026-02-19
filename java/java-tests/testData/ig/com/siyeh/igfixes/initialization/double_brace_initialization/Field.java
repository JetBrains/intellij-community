import java.util.ArrayList;
import java.util.List;

public class Field {
  static final List<Integer> list = new <caret>ArrayList<Integer>() {{
    for (int i = 0; i < 10; i++) {
      add(i);
    }
  }};
}