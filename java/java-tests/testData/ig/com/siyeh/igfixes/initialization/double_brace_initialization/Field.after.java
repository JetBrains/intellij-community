import java.util.ArrayList;
import java.util.List;

public class Field {
  static final List<Integer> list = new ArrayList<Integer>();

    static {
        for (int i = 0; i < 10; i++) {
          list.add(i);
        }
    }
}