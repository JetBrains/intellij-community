import org.jetbrains.annotations.Nullable;

class Npe {
  
  void foo(@Nullable SomeInterface x) {
    ((SomeInterfaceImpl)x).<warning descr="Method invocation 'bar' may produce 'NullPointerException'">bar</warning>();
  }

  interface SomeInterface {
    void bar();
  }

  class SomeInterfaceImpl implements SomeInterface {
    @Override
    public void bar() {
    }
  }
}