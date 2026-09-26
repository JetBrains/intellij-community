
interface BaseInterface {
  default void print() {
  }
  abstract public void print2(String a);
}


interface SecondInterface extends BaseInterface {
  abstract public void print();
  abstract public void print(String a);
  abstract public void print3(String a);
}


abstract class SecondClass implements BaseInterface {
  public void print2(String a){
  }
  abstract public void print3(String a);
}


class B extends SecondClass  implements SecondInterface {
  <caret>
}