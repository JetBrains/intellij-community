interface BaseInterface<T> {
  default T print() {
    return null;
  }
  abstract public T print2(String a);
}


interface SecondInterface<T> extends BaseInterface<T> {
  abstract public T print();
  abstract public T print(String a);
  abstract public T print3(String a);
}


abstract class SecondClass<T> implements BaseInterface<T> {
  public T print2(String a){
    return null;
  }
  abstract public T print3(String a);
}


class B<T> extends SecondClass<T> implements SecondInterface<T> {
    @Override
    public T print3(String a) {
        return null;
    }

    @Override
    public T print() {
        return null;
    }

    @Override
    public T print(String a) {
        return null;
    }
}
