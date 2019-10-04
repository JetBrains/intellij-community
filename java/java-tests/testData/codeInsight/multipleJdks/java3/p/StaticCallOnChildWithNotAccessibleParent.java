package p;

class Sample {
  public static void main(String[] args) {
    ChildClass.<error descr="Cannot resolve method 'foo' in 'ChildClass'">foo</error>();
  }
}
