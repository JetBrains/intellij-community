class C {
  public static void main(String[] args) {
    switch (args.length) {
      case 1:
        <error descr="Cannot resolve symbol 'foo'">foo</error>.bar;
        break;
      case 2:
        (<error descr="Cannot resolve symbol 'baz'">baz</error>).bar;
        break;
    }
  }
}