record Sample(int x, int y){}

public class Test {

  void test(Object o, Object o2){
    if (o instanceof Sample(int w, int y) && o2 instanceof Integer i){
      System.out.println(w+y);
      System.out.println(i);
    } else {
      System.out.println(<error descr="Cannot resolve symbol 'y'">y</error>);
      System.out.println(<error descr="Cannot resolve symbol 's'">s</error>);
    }

    if (o instanceof Sample(int w, int y) && (o2 instanceof Integer i || w > 0)){
      System.out.println(w+y);
      System.out.println(<error descr="Cannot resolve symbol 'i'">i</error>);
    }
  }
}
