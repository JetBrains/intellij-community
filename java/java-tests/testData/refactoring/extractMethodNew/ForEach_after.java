class Foo {
  {
    String[] args = getArgs();

    for(String arg : args) {
        newMethod(arg);
    }
  }

    private void newMethod(String arg) {
        System.out.println("arg = " + arg);
    }
}