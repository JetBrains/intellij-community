import java.util.ArrayList;
import java.util.List;

public class C {
  List<String> list = new ArrayList<>();

  void foo(int index) {
    newMethod(index);

    newMethod(index + 1);
  }

    private String newMethod(int i) {
        return list.get(i);
    }

    void bar() {
    newMethod(1-2);
  }
}
