import java.util.ArrayList;
import java.util.List;

public class C {
  List<String> list = new ArrayList<>();

  void foo(int index) {
    list.get(index);

    <selection>list.get(index + 1)</selection>;
  }
  void bar() {
    list.get(1-2);
  }
}
