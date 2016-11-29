import java.util.ArrayList;
import java.util.List;

class Main {
  void suspicious(Object[][] data) {
    List<String> stringList = new ArrayList<>();

    stringList.remove(<warning descr="'List<String>' may not contain objects of type 'Object[][]'">data</warning>);
    stringList.remove(<warning descr="'List<String>' may not contain objects of type 'Object[]'">data[0]</warning>);
    stringList.remove(<warning descr="Suspicious call to 'List.remove'">data[0][0]</warning>);

    stringList.contains(<warning descr="'List<String>' may not contain objects of type 'Object[][]'">data</warning>);
    stringList.contains(<warning descr="'List<String>' may not contain objects of type 'Object[]'">data[0]</warning>);
    stringList.contains(<warning descr="Suspicious call to 'List.contains'">data[0][0]</warning>);

    M<? extends String> m = new M<>();
    stringList.contains(m.get());

    M<? super String> m1 = new M<>();
    stringList.contains(<warning descr="Suspicious call to 'List.contains'">m1.get()</warning>);

    M<?> m2 = new M<>();
    stringList.contains(<warning descr="Suspicious call to 'List.contains'">m2.get()</warning>);
  }

  class M<T> {
    public T get() {
      return null;
    }
  }
}
