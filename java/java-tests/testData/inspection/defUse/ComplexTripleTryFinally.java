import java.io.IOException;

class Y {
  private static class Ex extends Exception {
    public Ex(Exception e) {
      super(e);
    }
  }

  private static class P {
  }

  private static class T {
  }

  private static class R {
    public T[] ts() {
      return new T[]{new T()};
    }
  }

  private static class M {
    public S s() {
      return new S();
    }
  }

  private static class S {
    public void c() throws IOException {
    }
  }

  private static class F {
    public void c() {
    }
  }

  private static class D {
    public M m;
    public S s;
    public F f;

    public R r() {
      return new R();
    }

    public P p() {
      return new P();
    }
  }

  private static class C {
    public D d() {
      return new D();
    }

    public void c() {
    }

    public Q q() {
      return new Q();
    }
  }

  private static class Q {
    public boolean a(T t) {
      return false;
    }
  }


  private static class J {
    public boolean c() {
      return false;
    }
  }


  private void foo(C c) throws Ex {
    final D d = c.d();
    Ex ex = null;
    try {
      final J j = m2(d.p());
      final boolean b = j.c();
      if (b) {
        m3(c);
      }
      else {
        for (T t : d.r().ts()) {
          c.c();
          if (c.q().a(t)) {
            m1(c, t);
          }
        }
      }
    }
    catch (Ex e) {
      ex = e;
    }
    finally {
      try {
        d.m.s().c();
      }
      catch (IOException e) {
        if (ex == null) {
          ex = new Ex(e);
        }
        else {
          System.out.println("1" + e);
        }
      }
      finally {
        try {
          d.s.c();
        }
        catch (IOException e) {
          if (ex == null) {
            ex = new Ex(e);
          }
          else {
            System.out.println("2 " + e);
          }
        }
        finally {
          d.f.c();
          if (ex != null) {
            throw ex;
          }
        }
      }
    }
  }

  private void m1(C c, T t) throws Ex {
  }

  private static J m2(P p) throws Ex {
    return new J();
  }

  private void m3(C c) throws Ex {
  }
}
