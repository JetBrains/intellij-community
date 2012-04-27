interface Intf { Intf XFOO = null; }
interface Intf2 extends Intf { Intf2 XFOO = null; }

public class Bar implements Intf2 {
  public static void fpp() {
    XF<caret>x
  }
}
