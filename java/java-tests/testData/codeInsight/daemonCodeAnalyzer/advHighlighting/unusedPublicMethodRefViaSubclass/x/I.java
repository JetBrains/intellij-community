package x;

public interface I {
    void ffffff();
}

class XI implements I {
  public void ffffff() {
  }

  public void <warning descr="Method 'ffffff2()' is never used">ffffff2</warning>() {
  }
}
