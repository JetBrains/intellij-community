interface Intf { Intf FOO = null; }
interface Intf2 extends Intf { Intf2 FOO = null; }

public class Bar implements Intf2 {
  public static void fpp() {
    F<caret>
  }
}
