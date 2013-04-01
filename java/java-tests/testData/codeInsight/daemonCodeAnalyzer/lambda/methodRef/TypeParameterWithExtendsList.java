interface AdditiveGroupValue<A> {
    A negative();
}

interface Vector<S extends AdditiveGroupValue<S>>  {
    default void foo() {
        Foo<S> negative = AdditiveGroupValue::negative;
    }
}

interface Foo<T> {
    T bar(T t);
}

class Test1 {
  interface AdditiveGroupValue<V extends AdditiveGroupValue<V>> {
  
      V plus(V other);
  
      V negative();
  
      default V minus(V other) {
          return self().plus(other.negative());
      }
  
      V self();
  
      Class<?> valueClass();
  
  }
  
  interface Vector<S extends AdditiveGroupValue<S>, V extends Vector<S, V>> extends AdditiveGroupValue<V> {
  
      S get(int i);
  
      int size();
  
      @Override
      default V plus(V other) {
          return Vectors.binaryComponentOp(self(), other, AdditiveGroupValue::plus);
      }
  
      @Override
      default V minus(V other) {
          return Vectors.binaryComponentOp(self(), other, AdditiveGroupValue::minus);
      }
  
      @Override
      default V negative() {
          return Vectors.unaryComponentOp(self(), AdditiveGroupValue::negative);
      }
  
      V valueOf(S[] components);
  
  }
  
  interface UnaryOperator<T> {
      T apply(T r);
  }
  
  interface BinaryOperator<T> {
      T apply(T r, T t);
  }
  static class Vectors {
  
      public static <S extends AdditiveGroupValue<S>, V extends Vector<S, V>> V unaryComponentOp(V v1, UnaryOperator<S> op) {
          int size = v1.size();
          S[] components = newComponentArray(v1.valueClass(), size);
          for (int i = 0; i < size; i++) {
              components[i] = op.apply(v1.get(i));
          }
          return v1.valueOf(components);
      }
  
      public static <S extends AdditiveGroupValue<S>, V extends Vector<S, V>> V binaryComponentOp(V v1, V v2, BinaryOperator<S> op) {
          int size = v1.size();
          S[] components = newComponentArray(v1.valueClass(), size);
          for (int i = 0; i < size; i++) {
              components[i] = op.apply(v1.get(i), v2.get(i));
          }
          return v1.valueOf(components);
      }
  
      protected static <S extends AdditiveGroupValue<S>> S[] newComponentArray(Class<?> componentClass, int size) {
          return null;
      }
  
  }
  
  
}