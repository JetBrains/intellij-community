class Test {
  {
    Holder h = null;
    Result<String> r1 = new Result<><error descr="'Result(D)' in 'Result' cannot be applied to '(Holder)'">(h)</error>;
    Result<String> r2 = Result.create<error descr="'create(K)' in 'Result' cannot be applied to '(Holder)'">(h)</error>;

    Holder dataHolder = null;
    Result<String> r3 = new Result<>(new Holder<>(dataHolder));
    Result<String> r4 = Result.create(new Holder<>(dataHolder));

    Result<String> r5 = new Result<>(Holder.create(dataHolder));
    Result<String> r6 = Result.create(Holder.create(dataHolder));

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
