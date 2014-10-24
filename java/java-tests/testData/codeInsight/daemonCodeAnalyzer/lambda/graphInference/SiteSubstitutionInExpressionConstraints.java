import java.util.Map;
import java.util.List;

//expected type parameter
class Sample<V> {

  public <K> Map<V,  K> test() {
    return null;
  }

  public <T> void fun(T t, V v) {
  }

  <M> M bar() {
    return null;
  }

  void run(Sample<Integer> sample) {
    fun(test(), bar());
    sample.fun(test(), sample.bar());
    sample.fun(test(), bar());
    fun(test(), sample.bar());

    fun(sample.test(), bar());
    fun(sample.test(), sample.bar());
    sample.fun(sample.test(), bar());
    sample.fun(sample.test(), sample.bar());
  }
}

//expected generic type
class Sample1<T> {
  public <S extends T> List<S> reverse() {
    return null;
  }

  public void foo(Sample1<Comparable> t)
  {
    newTreeSet(t.reverse());
    newTreeSet(reverse());

    t.newTreeSet(t.reverse());
    t.newTreeSet(reverse());
  }


  public <E> void newTreeSet(List<E> comparator) {}
}

