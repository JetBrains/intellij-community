import java.util.*;

class C {
  static final List EMPTY = new ArrayList(0);

  void m() {
    List<String> list = C.<error descr="Reference parameters are not allowed here"><String></error>EMPTY;
    System.out.println(list);
  }
}
