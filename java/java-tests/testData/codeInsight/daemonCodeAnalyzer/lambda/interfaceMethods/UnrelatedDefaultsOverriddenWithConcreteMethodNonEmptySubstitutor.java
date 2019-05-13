interface A<K> {
  default void replace(K v) {}
}

interface B<K> extends A<K> {
  void replace(K k);
}

abstract class AC<K> implements A<K> {}

class C<K> extends AC<K> implements B<K> {
  @Override
  public void replace(K k) {}
}

<error descr="Class 'D' must either be declared abstract or implement abstract method 'replace(K)' in 'B'">class <error descr="Class 'D' must either be declared abstract or implement abstract method 'replace(K)' in 'B'">D</error><K> extends AC<K> implements B<K></error> {}