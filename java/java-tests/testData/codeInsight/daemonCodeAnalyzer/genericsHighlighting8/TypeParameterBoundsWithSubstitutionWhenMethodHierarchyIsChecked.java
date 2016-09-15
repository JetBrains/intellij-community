import java.util.List;

interface A {
  String save(String world);
}

interface Test2 extends A, CR<String> {}
interface Test1 extends CR<String>, A {}
interface CR<T> {
  <S extends T> S       save(S var1);
  <S extends T> List<S> save(Iterable<S> var1);
}
