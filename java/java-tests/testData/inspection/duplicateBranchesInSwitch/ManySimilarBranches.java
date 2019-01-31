import java.util.*;

class C {
  private final Object lock = new Object();

  Map<Integer, List<String>> foo(int n, int k) {
    switch (n) {
      case 1: bar("A");break;
      case 2: bar("B");break;
      case 3: bar("C");break;
      case 4: bar("D");break;
      case 5: bar("E");break;
      case 6: bar("F");break;
      case 7: bar("G");break;
      case 8: bar("H");break;
      case 9: <weak_warning descr="Duplicate branch in 'switch' statement">bar("A");</weak_warning>break;

      case 11: return Collections.singletonMap(k + 1, Collections.singletonList("A"));
      case 12: return Collections.singletonMap(k + 2, Collections.singletonList("B"));
      case 13: return Collections.singletonMap(k + 3, Collections.singletonList("C"));
      case 14: return Collections.singletonMap(k + 4, Collections.singletonList("D"));
      case 15: return Collections.singletonMap(k + 5, Collections.singletonList("E"));
      case 16: return Collections.singletonMap(k + 6, Collections.singletonList("F"));
      case 17: return Collections.singletonMap(k + 7, Collections.singletonList("G"));
      case 18: return Collections.singletonMap(k + 8, Collections.singletonList("H"));
      case 19: <weak_warning descr="Duplicate branch in 'switch' statement">return Collections.singletonMap(k + 1, Collections.singletonList("A"));</weak_warning>

      case 21: synchronized (lock) { bar("A"); }break;
      case 22: synchronized (lock) { bar("B"); }break;
      case 23: synchronized (lock) { bar("C"); }break;
      case 24: synchronized (lock) { bar("D"); }break;
      case 25: synchronized (lock) { bar("E"); }break;
      case 26: synchronized (lock) { bar("F"); }break;
      case 27: synchronized (lock) { bar("G"); }break;
      case 28: synchronized (lock) { bar("H"); }break;
      case 29: <weak_warning descr="Duplicate branch in 'switch' statement">synchronized (lock) { bar("A"); }</weak_warning>break;

      case 31: assert k == 1;break;
      case 32: assert k == 2;break;
      case 33: assert k == 3;break;
      case 34: assert k == 4;break;
      case 35: assert k == 5;break;
      case 36: assert k == 6;break;
      case 37: assert k == 7;break;
      case 38: assert k == 8;break;
      case 39: <weak_warning descr="Duplicate branch in 'switch' statement">assert k == 1;</weak_warning>break;

      case 41: if (k > 0) bar("A"); else bar("B");break;
      case 42: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 43: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 44: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 45: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 46: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 47: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 48: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 49: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 50: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 51: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 52: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 53: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 54: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 55: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 56: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 57: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 58: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 59: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 61: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 62: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 63: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 64: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 65: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 66: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 67: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 68: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
      case 69: <weak_warning descr="Duplicate branch in 'switch' statement">if (k > 0) bar("A"); else bar("B");</weak_warning>break;
    }
    return Collections.emptyMap();
  }
  void bar(String s){}
}