import java.util.List;

class C {
    void <caret>method() {
    }
}

class Usage implements List {
    {
        final C c = new C();
        c.method();
        new Runnable() {
          public void run() {
            c.method();
          }
        }.run();
    }
}