record Sample(int x, int y){}

public class Test {

  void test(Object o, Object o2){
    switch (o) {
      case Sample(int r, int q) e when (r<0 || e.x()<0):
        System.out.println(r);
        break;
      case Integer i when o2 instanceof String str:
        System.out.println(str);
        System.out.println(i);
        break;
      case Sample(int r, int q) e:
        System.out.println(r);
        System.out.println(e.x());
        break;
      case String s:
        System.out.println(<error descr="Cannot resolve symbol 'r'">r</error>);
        System.out.println(s.length());
        break;
    }
  }
}
