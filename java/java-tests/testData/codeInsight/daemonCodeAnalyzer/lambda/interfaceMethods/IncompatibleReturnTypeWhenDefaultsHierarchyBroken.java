class Test {

  public interface UnmodifiableMap<K1, V1> {
  }

  public interface UnmodifiableArrayMap<K2, V2> extends UnmodifiableMap<K2, V2> {
  }


  public interface UnmodifiableCollection<E3> {
    <V> UnmodifiableMap<E3, V> mapToValues();

    interface Defaults0<E4> extends UnmodifiableCollection<E4> {
      @Override
      default <V> UnmodifiableMap<E4, V> mapToValues() {
        return null;
      }
    }
  }

  interface Defaults1<E5> extends UnmodifiableCollection.Defaults0<E5> {
  }

  public interface UnmodifiableList<E6> extends UnmodifiableCollection<E6> {
    @Override
    <V> UnmodifiableArrayMap<E6, V> mapToValues();

    interface Defaults<E7> extends UnmodifiableList<E7>, Defaults0<E7> {
      @Override
      default <V> UnmodifiableArrayMap<E7, V> mapToValues() {
        return null;
      }
    }
  }

  interface Defaults2<E8> extends Defaults1<E8>, UnmodifiableList.Defaults<E8>, UnmodifiableList<E8> {
  }
}


class TestFull {
  public interface Function1<R, T1> {
    R invoke(T1 argument1);
  }

  public interface UnmodifiableMap<K, V> extends UnmodifiableCollectionBase {
  }

  public interface UnmodifiableCollectionBase {
    interface Defaults extends UnmodifiableCollectionBase {
    }

    interface Decorator extends Defaults {
    }
  }

  public interface UnmodifiableEnumerator<E> {
    <T> UnmodifiableEnumerator<T> converted(Function1<T, E> converter);

    <T> UnmodifiableEnumerator<T> cast();

    interface Defaults<E> extends UnmodifiableEnumerator<E> {
    }

    interface Decorator<E> extends Defaults<E> {
    }
  }

  public interface UnmodifiableEnumerable<E> extends Iterable<E>, UnmodifiableCollectionBase {
    <T> UnmodifiableEnumerable<T> converted(Function1<? extends T, ? super E> converter);

    <T> UnmodifiableEnumerable<T> cast();

    <T extends E> UnmodifiableEnumerable<T> upCast();

    interface Defaults<E> extends UnmodifiableEnumerable<E>, UnmodifiableCollectionBase.Defaults {
    }

    interface Decorator<E> extends Defaults<E>, UnmodifiableCollectionBase.Decorator {
    }
  }

  public interface MutableEnumerator<T> extends UnmodifiableEnumerator<T> {
    interface Decorator<E> extends Defaults<E>, UnmodifiableEnumerator.Decorator<E> {
    }

    @Override
    <C> MutableEnumerator<C> converted(Function1<C, T> converter);

    @Override
    <C> MutableEnumerator<C> cast();

    interface Defaults<T> extends MutableEnumerator<T>, UnmodifiableEnumerator.Defaults<T> {
      @Override
      default <C> MutableEnumerator<C> converted(Function1<C, T> converter) {
        return null;
      }

      @Override
      default <C> MutableEnumerator<C> cast() {
        return null;
      }
    }
  }

  public interface MutableEnumerable<E> extends UnmodifiableEnumerable<E> {
    interface Defaults<E> extends MutableEnumerable<E>, UnmodifiableEnumerable.Defaults<E> {
    }

    interface Decorator<E> extends Defaults<E>, UnmodifiableEnumerable.Decorator<E> {
    }
  }

  public interface UnmodifiableCollection<E> extends UnmodifiableEnumerable<E> {
    boolean contains(E element);

    @Override
    <T> UnmodifiableCollection<T> converted(Function1<? extends T, ? super E> converter);

    <T> UnmodifiableCollection<T> converted(Function1<? extends T, ? super E> converter, Function1<? extends E, ? super T> reverter);

    @Override
    <T> UnmodifiableCollection<T> cast();

    @Override
    <T extends E> UnmodifiableCollection<T> upCast();

    <K> UnmodifiableMap<K, E> mapFromKeys(Function1<? extends K, ? super E> converter);

    <V> UnmodifiableMap<E, V> mapToValues(Function1<? extends V, ? super E> converter);

    interface Defaults<E> extends UnmodifiableCollection<E>, UnmodifiableEnumerable.Defaults<E> {
      @Override
      default <T> UnmodifiableCollection<T> converted(Function1<? extends T, ? super E> converter) {
        return null;
      }

      @Override
      default <T> UnmodifiableCollection<T> converted(Function1<? extends T, ? super E> converter,
                                                      Function1<? extends E, ? super T> reverter) {
        return null;
      }

      @Override
      default <T> UnmodifiableCollection<T> cast() {
        return null;
      }

      @Override
      default <T extends E> UnmodifiableCollection<T> upCast() {
        return null;
      }

      @Override
      default <K> UnmodifiableMap<K, E> mapFromKeys(Function1<? extends K, ? super E> converter) {
        return null;
      }

      @Override
      default <V> UnmodifiableMap<E, V> mapToValues(Function1<? extends V, ? super E> converter) {
        return null;
      }
    }

    interface Decorator<E> extends Defaults<E>, UnmodifiableEnumerable.Decorator<E> {
    }
  }

  public interface MutableCollection<E> extends UnmodifiableCollection<E>, MutableEnumerable<E> {
    @Override
    <T> MutableCollection<T> cast();

    @Override
    <T extends E> MutableCollection<T> upCast();

    interface Defaults<E> extends MutableCollection<E>, UnmodifiableCollection.Defaults<E>, MutableEnumerable.Defaults<E> {
      @Override
      default <T> MutableCollection<T> cast() {
        return null;
      }

      @Override
      default <T extends E> MutableCollection<T> upCast() {
        return null;
      }
    }

    interface Decorator<E> extends Defaults<E>, UnmodifiableCollection.Decorator<E>, MutableEnumerable.Decorator<E> {
    }
  }

  public interface UnmodifiableArrayMap<K, V> extends UnmodifiableMap<K, V> {
  }

  public interface UnmodifiableList<E> extends UnmodifiableCollection<E> {
    E get(int index);

    interface Decorator<E> extends Defaults<E>, UnmodifiableCollection.Decorator<E> {
      @SuppressWarnings("RedundantMethodOverride")
      //IntellijIdea blooper: "method is identical to its super method" (and "redundant suppression")
      @Override
      default boolean contains(E element) {
        //IntellijIdea blooper: "Unnecessarily Qualified Inner Class Access"
        //noinspection UnnecessarilyQualifiedInnerClassAccess
        return UnmodifiableList.Defaults.super.contains(element);
      }

      @Override
      default <T> UnmodifiableList<T> converted(Function1<? extends T, ? super E> converter) {
        return null;
      }
    }

    @Override
    <T> UnmodifiableList<T> converted(Function1<? extends T, ? super E> converter);

    @Override
    <T> UnmodifiableList<T> converted(Function1<? extends T, ? super E> converter, Function1<? extends E, ? super T> reverter);

    @Override
    <T> UnmodifiableList<T> cast();

    @Override
    <T extends E> UnmodifiableList<T> upCast();

    @Override
    <K> UnmodifiableArrayMap<K, E> mapFromKeys(Function1<? extends K, ? super E> converter);

    @Override
    <V> UnmodifiableArrayMap<E, V> mapToValues(Function1<? extends V, ? super E> converter);

    interface Defaults<E> extends UnmodifiableList<E>, UnmodifiableCollection.Defaults<E> {
      @Override
      default boolean contains(E element) {
        return false;
      }

      @Override
      default <T> UnmodifiableList<T> converted(Function1<? extends T, ? super E> converter) {
        return null;
      }

      @Override
      default <T> UnmodifiableList<T> converted(Function1<? extends T, ? super E> converter, Function1<? extends E, ? super T> reverter) {
        return null;
      }

      @Override
      default <T> UnmodifiableList<T> cast() {
        return null;
      }

      @Override
      default <T extends E> UnmodifiableList<T> upCast() {
        return null;
      }

      @Override
      default <K> UnmodifiableArrayMap<K, E> mapFromKeys(Function1<? extends K, ? super E> converter) {
        return null;
      }

      @Override
      default <V> UnmodifiableArrayMap<E, V> mapToValues(Function1<? extends V, ? super E> converter) {
        return null;
      }
    }
  }

  public interface MutableList<E> extends MutableCollection<E>, UnmodifiableList<E> {
    @Override
    <T> MutableList<T> cast();

    @Override
    <T extends E> MutableList<T> upCast();

    interface Defaults<E> extends MutableList<E>, MutableCollection.Defaults<E>, UnmodifiableList.Defaults<E> {
      @Override
      default <T> MutableList<T> cast() {
        return null;
      }

      @Override
      default <T extends E> MutableList<T> upCast() {
        return null;
      }
    }

    interface Decorator<E> extends MutableList.Defaults<E>, MutableCollection.Decorator<E>, UnmodifiableList.Decorator<E> {
    }
  }
}