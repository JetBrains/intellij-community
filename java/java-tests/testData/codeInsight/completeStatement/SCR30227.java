public abstract class A {
    abstract void f(boolean b);

    A IMPL = new A() {
      void f(boolean b) {
        if (b)
          f(true);
        else {
          f(false);
          f(false);
        }        
        for(int i = 0; i < 5; i++)
          f(true);
      }
    }; <caret>
}
