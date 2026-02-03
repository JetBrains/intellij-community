// "Fix all 'Pattern variable can be used' problems in file" "true"
class X {
  void test(Object obj, Object obj2, int i) {
    switch (i) {
      case 0:
        if (!(obj instanceof Number n)) return;
        if (!(obj2 instanceof Number)) return;
          System.out.println(n.longValue());
      case 1:
        Number n2 = (Number)obj2;
        System.out.println(n2.longValue());
    }
  }
}