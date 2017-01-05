class Test {
  {
    Holder h = null;
    Result<String> r1 = new Result<error descr="Cannot infer arguments"><></error>(h);
    Result<String> r2 = Result.create<error descr="'create(K)' in 'Result' cannot be applied to '(Holder)'">(h)</error>;

    Holder dataHolder = null;
    Result<String> r3 = new Result<error descr="Cannot infer arguments"><></error>(new Holder<>(dataHolder));
    Result<String> r4 = Result.create(new Holder<error descr="Cannot infer arguments"><></error>(dataHolder));

    Result<String> r5 = new Result<error descr="Cannot infer arguments"><></error>(Holder.create(dataHolder));
    Result<String> r6 = Result.create(Holder.create<error descr="'create(Holder<M>)' in 'Holder' cannot be applied to '(Holder)'">(dataHolder)</error>);

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
