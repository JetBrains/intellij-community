class LocationQuantity {
  public final int myInt = 3;

  public int <caret>method(int p) {
    return myInt + p;
  }

  public void sameClassContext() {
    int v = 1;
    v++;
    int v2 = myInt + v;
    Object o = new Object() {
      public void anonymousClassContext() {
         int av = myInt + 2;
      }
    };
  }

  public class Inner {
    private boolean myFlag;
    public int innerClassContext(int ip) {
      return myFlag ? myInt + ip : 0;
    }
  }
}

class DifferentClass {
  private int myInt;
  private void differentClassContext() {
    LocationQuantity lq = new LocationQuantity();
    int v = lq.myInt + myInt;
  }
}
