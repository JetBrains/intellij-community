// recusrsive ctr call


class s  {
    <error descr="Recursive constructor invocation">s()</error> {
        this();
    }

    <error descr="Recursive constructor invocation">s(int i)</error> {
        this(2);
    }
}

class c {
  c() {
    this(2);
  }

  <error descr="Recursive constructor invocation">c(int i)</error> {
    this(1,1);
  }
  <error descr="Recursive constructor invocation">c(int i, int k)</error> {
    this(1);
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