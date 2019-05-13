import java.io.File;
import java.io.IOException;
import java.util.*;

class Z {
  private float f1;
  private float f2;

  private static class R {
    public static final R N = new R();
  }

  private static class J {
  }

  private static class E {
    public static final E E1 = new E();
    public static final E E2 = new E();
    public static final E E3 = new E();
    public static final E E4 = new E();
  }

  private static class G {
    public static final G G1 = new G();

    public static G[] gs() {
      return new G[]{new G()};
    }
  }

  private static class C {
    public void c() {
    }

    public D d() {
      return new D();
    }
  }

  private static class B {
    public B(C c) {
    }

    public int n() {
      return 0;
    }

    public void c() {
    }

    public void f() {
    }
  }

  private static class L {
    public void s(C c, M m) {
    }

    public E b(C c, M m, H<J, N> h, B b) {
      return new E();
    }

    public String s() {
      return "";
    }

    public void f(C c, M m) {
    }
  }

  private static class Q<J, M> {

  }

  private static class N {
  }


  private static class H<J, M> {
    public void p(Q<J, M> q) throws IOException {
    }
  }

  private static class HB<J, M> extends H<J, M> {
    public HB(C c) {
    }
  }

  private static class T {
    public int s() {
      return 0;
    }
  }

  private static class M {

    public T t() {
      return new T();
    }

    public List<N> ns() {
      return new ArrayList<N>();
    }

    public String s() {
      return "";
    }
  }

  private static class F {
    public void b(C c, M m) {
    }

    public void c(C c) {
    }
  }

  private static class S {
    public void s(String s, List<String> ls) {
    }
  }


  private static class DM {
    public S s(N n) {
      return new S();
    }
  }

  private static class D {
    public F f = new F();
    public DM dm;
  }

  private D d = new D();

  private static class K {
    public List<L> ls(G g) {
      return new ArrayList<L>();
    }
  }

  private K k = new K();

  private static class Ex extends Exception {
    public Ex(String s) {
      super(s);
    }

    public Ex(Exception e) {
      super(e);
    }
  }


  private boolean foo(final C c, final M m) throws Ex, IOException {
    for (G g : G.gs()) {
      for (L l : k.ls(g)) {
        l.s(c, m);
      }
    }

    boolean b1 = false;
    boolean b2 = false;
    float c1 = f1;
    final int s = m.t().s();
    int p = 0;
    boolean b3;
    B b = new B(c);
    try {
      do {
        b3 = false;
        d.f.b(c, m);

        H<J, N> h = new HB<J, N>(c) {
          @Override
          public void p(Q<J, N> q) throws IOException {
            m5(c, m, q);
          }
        };
        if (!m4(c)) {
          final Map<N, Set<File>> map = m2(c, h);
          for (Map.Entry<N, Set<File>> e : map.entrySet()) {
            final N n = e.getKey();
            final Set<File> files = e.getValue();
            if (!files.isEmpty()) {
              final S mapping = c.d().dm.s(n);
              for (File srcFile : files) {
                mapping.s(srcFile.getPath(), new ArrayList<String>());
              }
            }
          }
        }

        L:
        for (G g : G.gs()) {
          final List<L> ls = k.ls(g);
          if (g == G.G1) {
            m9(b);
          }
          if (ls.isEmpty()) {
            continue;
          }

          try {
            for (L l : ls) {
              m8(c, m.ns());
              long l1 = System.nanoTime();
              int n1 = b.n();
              final E e = l.b(c, m, h, b);
              m7(l, System.nanoTime() - l1, b.n() - n1);

              b1 |= (e != E.E1);

              if (e == E.E2) {
                throw new Ex("Fail " + l.s());
              }
              c.c();
              if (e == E.E3) {
                b3 = true;
              }
              else if (e == E.E4) {
                if (!b2 && !m4(c)) {
                  System.out.println("1 " + l.s() + ":" + m.s());
                  b2 = true;
                  try {
                    c.d().f.c(c);
                    m1(c, R.N, m, null);
                    f2 -= (p * s) / c1;
                    c1 = f1;
                    p = 0;
                    b3 = true;
                    b.c();
                    break L;
                  }
                  catch (Exception ex) {
                    throw new Ex(ex);
                  }
                }
                else {
                  System.out.println("2 " + l.s());
                }
              }

              p++;
              m6(c, s / (c1));
            }
          }
          finally {
            final boolean b4 = m3(c, h, m);
            if (b4) {
              b3 = true;
            }
            if (b3 && !b2) {
              f2 -= (p * s) / c1;
              c1 += f1;
              f2 += (p * s) / c1;
            }
          }
        }
      }
      while (b3);
    }
    finally {
      m9(b);
      b.f();
      b.c();
      for (G g : G.gs()) {
        for (L l : k.ls(g)) {
          l.f(c, m);
        }
      }
    }

    return b1;
  }

  private void m1(C c, R r, M m, Object o) {
  }

  private static Map<N, Set<File>> m2(C c, H<J, N> h) {
    return new HashMap<N, Set<File>>();
  }

  private static boolean m3(C c, H<J, N> h, M m) {
    return c != null && h != null && m != null;
  }

  private boolean m4(C c) {
    return c != null && f1>1;
  }

  private void m5(C c, M m, Q<J, N> q) {
  }

  private void m6(C c, float v) {
  }

  private void m7(L l1, long l, int i) {
  }

  private void m8(C c, List<N> ns) {
  }

  private void m9(B b) {
  }
}
