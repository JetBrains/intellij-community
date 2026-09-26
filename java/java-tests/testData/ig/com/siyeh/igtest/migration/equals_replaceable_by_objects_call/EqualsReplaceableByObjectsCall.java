class EqualsReplaceableByObjectsCall {
  void yyy(Object a, Object b) {
    boolean c = <info descr="'(a != null) && a.equals(b)' replaceable by 'Objects.equals()' expression">(a != null) && a.equals(b)</info>;
    boolean d = <warning descr="'(a != b) && (a == null || !a.equals(b))' replaceable by 'Objects.equals()' expression">(a != b) && (a == null || !a.equals(b))</warning>;
    boolean e = <warning descr="'((a) == (b)) || ((a) != (null) && (a).equals((b)))' replaceable by 'Objects.equals()' expression">((a) == (b)) || ((a) != (null) && (a).equals((b)))</warning>;
  }

  void ignoreNullityCheck(Object a, Object b) {
    boolean c = <info descr="'a.equals(b)' replaceable by 'Objects.equals()' expression">a.equals(b)</info>;
  }

  void bar(T x, T y, T z) {
    boolean b = !<info descr="'x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s.equals(y.s)</info>;
    boolean c = <info descr="'x.s != null && x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s != null && x.s.equals(y.s)</info>;
    boolean d = y.s != null && <info descr="'x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s.equals(y.s)</info>;
    boolean e = <info descr="'x.s == null || !x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s == null || !x.s.equals(y.s)</info>;
    boolean f = <warning descr="'x.s != y.s && (x.s == null || !x.s.equals(y.s))' replaceable by 'Objects.equals()' expression">x.s != y.s && (x.s == null || !x.s.equals(y.s))</warning>;
    boolean g = x.s != y.s || (<info descr="'x.s == null || !x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s == null || !x.s.equals(y.s)</info>);
    boolean h = x.s != y.s && (z.s == null || !<info descr="'x.s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.s.equals(y.s)</info>);
  }

  void baz(T x, T y) {
    boolean b = <info descr="'x.copy().equals(y.copy())' replaceable by 'Objects.equals()' expression">x.copy().equals(y.copy())</info>;
    boolean c = <info descr="'x != null && x.equals(y.copy())' replaceable by 'Objects.equals()' expression">x != null &&  x.equals(y.copy())</info>;
    boolean d = <info descr="'x == null || !x.equals(y.copy())' replaceable by 'Objects.equals()' expression">x == null || !x.equals(y.copy())</info>;
    boolean e = x.copy() != null && <info descr="'x.copy().equals(y)' replaceable by 'Objects.equals()' expression">x.copy().equals(y)</info>;
    boolean f = x.copy() == null || !<info descr="'x.copy().equals(y)' replaceable by 'Objects.equals()' expression">x.copy().equals(y)</info>;
    boolean g = x.copy().s != null && <info descr="'x.copy().s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.copy().s.equals(y.s)</info>;
    boolean h = x.copy().s == null || !<info descr="'x.copy().s.equals(y.s)' replaceable by 'Objects.equals()' expression">x.copy().s.equals(y.s)</info>;
    boolean i = x.s == y.copy().s || <info descr="'(x).s != null && (x.s).equals(y.copy().s)' replaceable by 'Objects.equals()' expression">(x).s != null &&  (x.s).equals(y.copy().s)</info>;
    boolean j = x.s != y.copy().s && (<info descr="'(x).s == null || !(x.s).equals(y.copy().s)' replaceable by 'Objects.equals()' expression">(x).s == null || !(x.s).equals(y.copy().s)</info>);
  }

  void arr(T[] a, T[] b, int i) {
    boolean c = <info descr="'a[i] != null && a[i].equals(b[i])' replaceable by 'Objects.equals()' expression">a[i] != null && a[i].equals(b[i])</info>;
    boolean d = <warning descr="'a[i] == null ? b[i] == null : a[i].equals(b[i])' replaceable by 'Objects.equals()' expression">a[i] == null ? b[i] == null : a[i].equals(b[i])</warning>;
    boolean e = a[i++] != null && <info descr="'a[i++].equals(b[i++])' replaceable by 'Objects.equals()' expression">a[i++].equals(b[i++])</info>;
    boolean f = a[--i] != null && <info descr="'a[--i].equals(b[--i])' replaceable by 'Objects.equals()' expression">a[--i].equals(b[--i])</info>;
  }

  static class T {
    String s;
    T copy() { T t = new T(); t.s = s; return t; }
  }

  static class X extends T {
    public boolean equals(Object o) {
      return super.equals(o);
    }

    boolean same(T t) {
      return this == t || this != null && this.equals(t);
    }

    boolean different(T t) {
      return <warning descr="'t != this && (t == null || !t.equals(this))' replaceable by 'Objects.equals()' expression">t != this && (t == null || !t.equals(this))</warning>;
    }
  }

  static class A {
    static String b;
    static class B {
      static String c;
    }
  }
  static boolean ab1(String s) {
    return <info descr="'A.b.equals(s)' replaceable by 'Objects.equals()' expression">A.b.equals(s)</info>;
  }
  static boolean ab2(String s) {
    return <info descr="'A.b != null && A.b.equals(s)' replaceable by 'Objects.equals()' expression">A.b != null && A.b.equals(s)</info>;
  }
  static boolean ab3(String s) {
    return <warning descr="'A.b == s || A.b != null && A.b.equals(s)' replaceable by 'Objects.equals()' expression">A.b == s || A.b != null && A.b.equals(s)</warning>;
  }
  static boolean abc(String s) {
    return <warning descr="'A.B.c == s || A.B.c != null && A.B.c.equals(s)' replaceable by 'Objects.equals()' expression">A.B.c == s || A.B.c != null && A.B.c.equals(s)</warning>;
  }

  boolean literal(Object o) {
    return "literal".equals(o);
  }

  boolean boxed(Object o) {
    final Integer n = 1;
    return n.equals(o);
  }

  static final String CONSTANT = "constant";
  boolean constant(Object o) {
    return CONSTANT.equals(o);
  }

  boolean constantLiteral(Object o) {
    return ("a" + "b").equals(o);
  }

  static final String CONSTANT_EXPR = CONSTANT + 1;
  boolean constantExpr(Object o) {
    return CONSTANT_EXPR.equals(o);
  }

  static final String NULL_CONSTANT = null;
  boolean nullConstant(Object o) {
    return <info descr="'NULL_CONSTANT.equals(o)' replaceable by 'Objects.equals()' expression">NULL_CONSTANT.equals(o)</info>;
  }

  static final Object NEW_CONSTANT = new Object();
  boolean newConstant(Object o) {
    return NEW_CONSTANT.equals(o);
  }

  static final Object[] ARRAY_CONSTANT = {};
  boolean arrayConstant(Object o) {
    return ARRAY_CONSTANT.equals(o);
  }

  static final Class<?> CLASS_CONSTANT = String.class;
  boolean stringConstant(Object o) {
    return CLASS_CONSTANT.equals(o);
  }

  final String FINAL_FIELD = "finalField";
  boolean finalField(Object o) {
    return FINAL_FIELD.equals(o);
  }

  static class Ternary {
    String s;
    boolean eq1(Ternary t) {
      return <warning descr="'s == null ? t.s == null : s.equals(t.s)' replaceable by 'Objects.equals()' expression">s == null ? t.s == null : s.equals(t.s)</warning>;
    }
    boolean eq2(Ternary t) {
      return (<warning descr="'(s != null) ? (s.equals(t.s)) : (t.s == null)' replaceable by 'Objects.equals()' expression">(s != null) ? (s.equals(t.s)) : (t.s == null)</warning>);
    }
    boolean ne1(Ternary t) {
      return <warning descr="'s == null ? t.s != null : !s.equals(t.s)' replaceable by 'Objects.equals()' expression">s == null ? t.s != null : !s.equals(t.s)</warning>;
    }
    boolean ne2(Ternary t) {
      return <warning descr="'(s != null) ? (!s.equals(t.s)) : !(t.s == null)' replaceable by 'Objects.equals()' expression">(s != null) ? (!s.equals(t.s)) : !(t.s == null)</warning>;
    }
    boolean notMatches1(Ternary t) {
      return s == null ? t.s != null : <info descr="'s.equals(t.s)' replaceable by 'Objects.equals()' expression">s.equals(t.s)</info>;
    }
    boolean notMatches2(Ternary t) {
      return s != null ? t.s != null : <info descr="'s.equals(t.s)' replaceable by 'Objects.equals()' expression">s.equals(t.s)</info>;
    }
    boolean notMatches3(Ternary t) {
      return ((s != null) ? !(<info descr="'s.equals(t.s)' replaceable by 'Objects.equals()' expression">s.equals(t.s)</info>) : (t.s == null));
    }
    boolean notMatches4(Ternary t) {
      return s == null ? t.s == null : !<info descr="'s.equals(t.s)' replaceable by 'Objects.equals()' expression">s.equals(t.s)</info>;
    }
    boolean notMatches5(Ternary t) {
      return s != null ? <info descr="'s.equals(t.s)' replaceable by 'Objects.equals()' expression">s.equals(t.s)</info> : t.s != null;
    }
    boolean notMatches6(Ternary t) {
      return s == null ? !<info descr="'s.equals(t.s)' replaceable by 'Objects.equals()' expression">s.equals(t.s)</info> : t.s != null;
    }
    boolean notMatches7(Ternary t) {
      return s == null ? t.s == null : <info descr="'t.s.equals(s)' replaceable by 'Objects.equals()' expression">t.s.equals(s)</info>;
    }
    boolean notMatches8(Ternary t) {
      return (s != null) ? (<info descr="'t.s.equals(s)' replaceable by 'Objects.equals()' expression">t.s.equals(s)</info>) : (t.s == null);
    }
  }
}