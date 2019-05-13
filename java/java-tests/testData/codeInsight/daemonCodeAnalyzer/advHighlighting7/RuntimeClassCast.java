class Test {
  <S extends Runnable> S f() {
    return null;
  }

  {
    String m  <warning descr="Though assignment is formal correct, it could lead to ClassCastException at runtime. Expected: 'String', actual: 'Runnable & String'">=</warning> f();
    System.out.println(m);
  }
}