class A {
  A() {}
  A(int i) {}
}

class B extends A {
  B() {
    int i = 3;
    super();
  }
  B(int i) {
    this();
   <error descr="Only one explicit constructor call allowed in constructor">super(2)</error>;
  }
  B(char i) {
    super(4);
   <error descr="Only one explicit constructor call allowed in constructor">this()</error>;
  }

  B(String s) {
    try {
     <error descr="Call to 'super()' must be a top level statement in constructor body">super(2)</error>;
    }
    finally {
    }
  }
  B(String s, int i) {
    {
     <error descr="Call to 'super()' must be a top level statement in constructor body">super(2)</error>;
    }
  }
  B(boolean b, int i) {
    if (false) <error descr="return not allowed before 'super()' call">return;</error>
    super(i);
  }

  void f() {
      <error descr="Call to 'super()' only allowed in constructor body">super()</error>;
  }
  void g() {
      <error descr="Call to 'this()' only allowed in constructor body">this()</error>;
  }

}