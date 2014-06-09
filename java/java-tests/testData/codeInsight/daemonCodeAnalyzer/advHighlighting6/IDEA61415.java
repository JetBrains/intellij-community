class Test {
  Runnable r = new Runnable() {
    <error descr="Modifier 'private' not allowed here">private</error> class Foo {}
    @Override
    public void run() {

    }
  };
}

