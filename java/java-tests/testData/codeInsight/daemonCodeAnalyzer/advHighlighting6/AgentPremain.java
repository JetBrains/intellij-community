package java.lang.instrument;

class Instrumentation {} 

class Foo {

  public void agentmain(String args, Instrumentation i) {
    System.out.println(args);
    System.out.println(i);
  }
}