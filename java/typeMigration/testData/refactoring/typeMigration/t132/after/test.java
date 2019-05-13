interface I {
  void f();
}

interface J extends I {
  void g();
}

public interface Test {
  void h(I i);
}

class B implements Test {
  @Override
  public void h(I i) {
    i.f();
  }
} 

class C implements Test {
  @Override
  public void h(I i) {
    i.f();
  }
}