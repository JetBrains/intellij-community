import java.util.List;

class Test {
  List<Pojo> things;

  void foo() {
    while(true) {
      <selection>Pojo x = things.get(0);

      if(x.it > 0) {
        break;
      }
      things.remove(x);</selection> 
      System.out.println(x.it);
    }
  }

  static class Pojo {
    double it;
    Pojo(double w) {
      it = w;
    }
  }
}