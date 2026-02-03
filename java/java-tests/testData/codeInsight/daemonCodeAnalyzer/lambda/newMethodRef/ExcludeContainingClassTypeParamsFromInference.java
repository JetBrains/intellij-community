class TestJ8 {

  interface Func<Af, Bf> {
    Bf f(Af a);
  }

  class List<A> {

    <Bm> List<Bm> map(Func<A, Bm> f) {
      return null;
    }

    <Bb> List<Bb> bind(Func<A, List<Bb>> f) {
      return null;
    }

    <B> List<B> apply(final List<Func<A, B>> lf) {
      return lf.bind(this::map);
    }

  }
}