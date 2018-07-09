import org.jetbrains.annotations.Nullable;

import java.util.List;

class Test {
  List<Pojo> things;

  void foo() {
    while(true) {
        Pojo x = newMethod();
        if (x == null) break;
        System.out.println(x.it);
    }
  }

    @Nullable
    private Pojo newMethod() {
        Pojo x = things.get(0);

        if(x.it > 0) {
            return null;
        }
        things.remove(x);
        return x;
    }

    static class Pojo {
    double it;
    Pojo(double w) {
      it = w;
    }
  }
}