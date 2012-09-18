public abstract class A2 {
    public void a() {
      b();
    }

    protected abstract void b();
}

class B2 extends A2 {

    @Override
    protected void b() {
  }
}
