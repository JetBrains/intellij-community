// "Fix all 'Pattern variable can be used' problems in file" "true"
class X {
  void test(Object obj, Object obj2, int i) {
    switch (i) {
      case 0:
        if (!(obj instanceof Number)) return;
        if (!(obj2 instanceof Number)) return;
        Number <caret>n = (Number)obj;
        System.out.println(n.longValue());
      case 1:
        Number n2 = (Number)obj2;
        System.out.println(n2.longValue());
    }
  }
}