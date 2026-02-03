class Foo {
  {
    String[] args = getArgs();

    for(String arg : args) {
       <selection>System.out.println("arg = " + arg);</selection>
    }
  }
}