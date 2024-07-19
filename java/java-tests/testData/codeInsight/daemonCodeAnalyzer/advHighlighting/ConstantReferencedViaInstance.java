class Example extends Zuper{
  public class Inner{
    static String i;
  }

  Example() {
    super(new <error descr="Cannot reference 'Inner' before superclass constructor is called">Inner</error>().i);
  }
}

class Zuper {
  Zuper(Object o) {
  }
}