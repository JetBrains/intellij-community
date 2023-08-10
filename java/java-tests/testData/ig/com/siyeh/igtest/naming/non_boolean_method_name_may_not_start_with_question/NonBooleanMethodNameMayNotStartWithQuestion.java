class NonBooleanMethodNameMayNotStartWithQuestion {

  public void <warning descr="Non-boolean method name 'areYouAllRight' starts with a question word">areYouAllRight</warning>() {
  }

  public void justSomeWords() {}
}
abstract class MyCollection<E> {

  public boolean add(E e) {
    return false;
  }

  public boolean remove(Object o) {
    return false;
  }
}