class A implements Runnable {
  @Override
  public void <warning descr="Overriding methods are not annotated with '@Override'">run</warning>() {

  }
}

class B extends A {
  public void <warning descr="Missing '@Override' annotation on 'run()'">run</warning>() {
  }
}