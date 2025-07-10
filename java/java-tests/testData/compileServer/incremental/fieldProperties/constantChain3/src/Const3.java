public class Const3 extends Const2 {
  public void data() {
    Object[][] arr = new Object[][]{{A2}};
  }
}

class Dummy1 {
  // because of this additional class definition the order of events will be:
  //   1. Const3 class generated
  //   2. constant refs registered
  // compared to the case when only one top-level definition were present in the file:
  //   1. constant refs registered
  //   2. Const3 class generated
}
