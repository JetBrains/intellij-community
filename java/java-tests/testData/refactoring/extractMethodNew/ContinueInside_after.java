class Test {
  String foo(String[] args) {

      if (newMethod(args)) return null;

      return null;
  }

    private boolean newMethod(String[] args) {
        for(String arg : args) {
          if (arg == null) continue;
          System.out.println(arg);
        }
        if (args.length == 0) return true;
        return false;
    }
}
