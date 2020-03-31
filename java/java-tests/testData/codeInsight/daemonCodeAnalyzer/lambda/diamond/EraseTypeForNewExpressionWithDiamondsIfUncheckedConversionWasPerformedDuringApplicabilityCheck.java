class Test {
  {
    Holder h = null;
    Result<String> r1 = new Result<error descr="Cannot infer arguments"><></error>(h);
    Result<String> r2 = <error descr="Incompatible types. Found: 'Result<Holder>', required: 'Result<java.lang.String>'">Result.create(h);</error>

    Holder dataHolder = null;
    Result<String> r3 = new Result<error descr="Cannot infer arguments"><></error>(new Holder<>(dataHolder));
    Result<String> r4 = <error descr="Incompatible types. Found: 'Result<Holder>', required: 'Result<java.lang.String>'">Result.create(new Holder<>(dataHolder));</error>

    Result<String> r5 = new Result<error descr="Cannot infer arguments"><></error>(Holder.create(dataHolder));
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
