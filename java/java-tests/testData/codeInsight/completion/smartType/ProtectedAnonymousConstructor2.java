import pkg.Bar;

class Goo {

  public void foo() {
    new Bar<String>(new ArrL<caret>) {}
  }

}