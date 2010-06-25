public class X {
    {
        Proc<String> procs = new Proc<String>() {
            public void f(String <caret>s) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        };
        Proc<Integer> proci = new Proc<Integer>() {
            public void f(Integer s) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        };

        procs.f(<flown1>"");
        proci.f(0);
    }
}

interface Proc<T> {
      void f(T t);
}
