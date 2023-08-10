class Test {
  {
    Holder h = null;
    Result<String> r1 = <error descr="Incompatible types. Found: 'Result<Holder>', required: 'Result<java.lang.String>'">new Result<>(h);</error>
    Result<String> r2 = <error descr="Incompatible types. Found: 'Result<Holder>', required: 'Result<java.lang.String>'">Result.create(h);</error>

    Holder dataHolder = null;
    Result<String> r3 = <error descr="Incompatible types. Found: 'Result<Holder>', required: 'Result<java.lang.String>'">new Result<>(new Holder<>(dataHolder));</error>
    Result<String> r4 = <error descr="Incompatible types. Found: 'Result<Holder>', required: 'Result<java.lang.String>'">Result.create(new Holder<>(dataHolder));</error>

    Result<String> r5 = <error descr="Incompatible types. Found: 'Result<Holder>', required: 'Result<java.lang.String>'">new Result<>(Holder.create(dataHolder));</error>
    Result<String> r6 = <error descr="Incompatible types. Found: 'Result<Holder>', required: 'Result<java.lang.String>'">Result.create(Holder.create(dataHolder));</error>

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
