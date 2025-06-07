class Test {
  {
    Holder h = null;
    Result<String> r1 = new <error descr="Incompatible types. Found: 'Result<Holder>', required: 'Result<java.lang.String>'">Result<></error>(h);
    Result<String> r2 = Result.<error descr="Incompatible types. Found: 'Result<Holder>', required: 'Result<java.lang.String>'">create</error>(h);

    Holder dataHolder = null;
    Result<String> r3 = new <error descr="Incompatible types. Found: 'Result<Holder>', required: 'Result<java.lang.String>'">Result<></error>(new Holder<>(dataHolder));
    Result<String> r4 = Result.<error descr="Incompatible types. Found: 'Result<Holder>', required: 'Result<java.lang.String>'">create</error>(new Holder<>(dataHolder));

    Result<String> r5 = new <error descr="Incompatible types. Found: 'Result<Holder>', required: 'Result<java.lang.String>'">Result<></error>(Holder.create(dataHolder));
    Result<String> r6 = Result.<error descr="Incompatible types. Found: 'Result<Holder>', required: 'Result<java.lang.String>'">create</error>(Holder.create(dataHolder));

  }
}
class Result<D> {
  Result(D data) {}

  static <K> Result<K> create(K k) {
    return null;
  }
}

class Holder<E> {
  Holder(Holder<E> holder) {}

  static <M> Holder<M> create(Holder<M> m) {
    return null;
  }
}
