// recusrsive ctr call


class s  {
    s() {
        <error descr="Recursive constructor call">this()</error>;
    }

    s(int i) {
        <error descr="Recursive constructor call">this(2)</error>;
    }
}

class c {
  c() {
    this(2);
  }

  c(int i) {
    <error descr="Recursive constructor call">this(1,1)</error>;
  }
  c(int i, int k) {
    <error descr="Recursive constructor call">this(1)</error>;
  }
}

class cv {
  cv() {
    this(1);
  }

  cv(int i) {
    this(1,2);
  }

  cv(int i,int j) {}
}
class X {
  private final int value;
  
  X() {
    <error descr="Recursive constructor call">this()</error>;
    <error descr="Cannot assign final field 'value' after chained constructor call">value</error> = 1;
  }
}