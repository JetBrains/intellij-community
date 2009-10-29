class Test {
  String foo(String[] args) {
    <selection>
    for(String arg : args) {
      if (arg == null) continue;
      System.out.println(arg);
    }
    if (args.length == 0) return null;
    </selection>
    return null;
  }
}
