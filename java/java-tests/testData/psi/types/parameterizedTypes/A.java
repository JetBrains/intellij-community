public abstract class A<X> {

  public abstract class B<Y> {
  }

  abstract B<String> methodA();

  abstract A<Number>.B<String> methodB();

}