import java.lang.Integer;
import java.lang.String;

class X {
  void m() {

    Integer i1 = new Integer(0);
    Integer i2 = new Integer(2);

    boolean b = i1.compareT<caret>o(i2) == 0;

  }
}