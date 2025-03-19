class Super<T> {
  Super() {
  }

  Super(int a, int b) {
  }

  Super(T tttt) {}

  void overloaded(String <warning descr="Parameter name 'str' is different from parameter 'boo' in the overloaded method">str</warning>) {
    overloaded(str, 0);
  }

  void overloaded(String boo, int start) {

  }

  public void method(T <warning descr="Parameter name 't' is different from parameter 'other' in the overloaded method">t</warning>) {
    method(t, 1);
  }
  public void method(T other, int i) {}
}

class Sub extends Super<String> {
  Sub(int <warning descr="Parameter name 'c' is different from parameter 'a' in the super constructor">c</warning>, int b) {
    super(c, b);
  }

  Sub(int a, int b, int c) {
  }

  Sub(String ssss) { // type is now concrete, allow a different name
    super(ssss);
  }

  @Override
  void overloaded(String <warning descr="Parameter name 'string' is different from parameter 'str' in the super method">string</warning>) {
  }

  @Override
  public void method(String s) {} // type is now concrete, allow a different name
}
class Sub2<S> extends Super<S> {
  Sub2(S other) { // no warn on type parameters
    super(other);
  }

  @Override
  public void method(S other) {} // no warn on type parameters
}

class A<T> {
  A(T name, int age) {}
  A() {}
}

class B<T> extends A<T> {
  B(T street, // no warn on type parameters
    int <warning descr="Parameter name 'number' is different from parameter 'age' in the super constructor">number</warning>) {
    super(street, number);
  }
}

class C {
  C(String name, int age) {}
  C() {}
}

class D extends C {
  D(String street, int number) {
    super();
  }
}

class E {
  E(String name, int age) {}
}

class F extends E {
  F(String name, int age, int weight) {
    super(name, age);
  }

  F(String <warning descr="Parameter name 'text' is different from parameter 'name' in the overloaded constructor">text</warning>, int age) {
    this(text, age, 100);
  }
}

class G {
  G(String name, int age) {}
}

class H extends G {
  H(Object street, int <warning descr="Parameter name 'number' is different from parameter 'age' in the super constructor">number</warning>) {
    super(street.toString(), number);
  }

  H(String <warning descr="Parameter name 'title' is different from parameter 'name' in the super constructor">title</warning>, int age, int number) {
    super(title, age);
  }

  private void configureMethod(final Iterable<Class<? extends String>> registeredAnnotations, final Integer method) {
    for (final Class<? extends String> registeredAnnotation : registeredAnnotations) {
      configureMethod(registeredAnnotation, method);
    }
  }

  private void configureMethod(final Class<? extends String> daoAnnotation, final Integer method) {
  }
}
class I {
  static J applicationContext = null;

  public static <T> T getBean(final Class<T> beanType) {
    return applicationContext.getBean(beanType);
  }
}
class J { // unrelated to I
  public <T> T getBean(Class<T> requiredType) {
    return null;
  }
}
class Test {

  static int method(int a, int b) {
    return a + b;
  }
  static int method(int a, int b, int c) {
    return method (a, method(b, c));
  }
  static int method(int a, int b, int c, int d) {
    return method(a, method(b, c, d));
  }

}