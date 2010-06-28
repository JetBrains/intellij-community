public class X {
    {
        Proc<String> procs = new Proc<String>() {
            public void f(String s) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        };
        Proc<Integer> proci = new Proc<Integer>() {
            public void f(Integer <caret>s) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        };

        procs.f("");
        proci.f(<flown1>0);
    }
}

interface Proc<T> {
      void f(T t);
}
