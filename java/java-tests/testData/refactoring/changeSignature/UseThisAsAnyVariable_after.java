import java.util.List;

class C {
    void method(List l) {
    }
}

class Usage implements List {
    {
        final C c = new C();
        c.method(this);
        new Runnable() {
          public void run() {
            c.method(Usage.this);
          }
        }.run();
    }
}