import java.util.ArrayList;
import java.util.List;

public class C {
  List<String> list = new ArrayList<>();

  void foo(int index) {
    System.out.println(list.get(index));

    <selection>System.out.println(list.get(index + 1))</selection>;
  }
  void bar() {
    System.out.println(list.get(1-2));
  }
}
