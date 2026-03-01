package com.example;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Function;

abstract class ImmutableMap<K, V> implements Map<K, V> {
  public static <K, V> Builder<K, V> builder() {
    return new Builder<>();
  }

  public static class Builder<K, V> {
    public Builder<K, V> put(K key, V value) {
      throw new UnsupportedOperationException();
    }

    public ImmutableMap<K, V> buildOrThrow() {
      throw new UnsupportedOperationException();
    }
  }
}

interface Staircase {}

interface ListenableFuture<V extends Object> extends Future<V> {
  void addListener(Runnable listener, Executor executor);
}

interface Hesitate<V extends Staircase> extends ListenableFuture<V> {}

final class Provision implements Staircase {}
final class Charter implements Staircase {}
final class Professor implements Staircase {}
final class Twilight implements Staircase {}
final class Residence implements Staircase {}

final class Salad {}

final class Science implements Staircase {
  public Professor getProfessor() {
    throw new UnsupportedOperationException();
  }

  public Charter getCharter() {
    throw new UnsupportedOperationException();
  }

  public Twilight getTwilight() {
    throw new UnsupportedOperationException();
  }

  public Residence getResidence() {
    throw new UnsupportedOperationException();
  }
}

interface Sketch {
  Hesitate<Charter> strengthen(Salad s, Professor p);

  Hesitate<Provision> supervise(Salad s, Twilight t);
}

enum City {
  LARGE_CITY,
  SMALL_CITY
}

final class MyClass {

  public void foo() {

    ImmutableMap<City, CityEndorser<? extends Staircase, ? extends Staircase, ? extends Staircase>>
      local =
      ImmutableMap
        .<City, CityEndorser<? extends Staircase, ? extends Staircase, ? extends Staircase>>
          builder()
        .put(
          City.LARGE_CITY,
          new CityEndorser<>(
            Science::getProfessor,
            Science::getCharter,
            s -> s::strengthen))
        .put(
          City.SMALL_CITY,
          new CityEndorser<>(
            Science::getTwilight,
            Science::getResidence,
            // Exception on getFunctionalInterfaceType() of the lambda expression.
            s -> s::supervise))
        .buildOrThrow();
  }

  private interface Manufacturer<Q extends Staircase, S extends Staircase> {
    Hesitate<S> go(Salad s, Q q);
  }

  private static class CityEndorser<Q extends Staircase, P extends Staircase, S extends Staircase> {
    private CityEndorser(
      Function<Science, Q> qExtractor,
      Function<Science, P> pExtractor,
      Function<Sketch, Manufacturer<Q, S>> m) {}
  }
}
