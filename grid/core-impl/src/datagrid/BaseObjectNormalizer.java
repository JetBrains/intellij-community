package com.intellij.database.datagrid;

import com.intellij.util.containers.ClassMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class BaseObjectNormalizer implements ObjectNormalizer {
  private final MyMap<Object> myToObject = new MyMap<>();

  protected Converter<Object, Object> identity = new Converter<>() {
    @Override
    public Object convert(Object o, GridColumn column) {
      return o;
    }
  };

  {
    put(Object[].class, identity);
    put(Boolean.class, identity);
    put(Number.class, identity);
    put(Timestamp.class, identity);
    put(Time.class, identity);
    put(String.class, identity);
    addToBoxedArrayConverters();
  }

  @Override
  public @Nullable Object objectToObject(@Nullable Object o, GridColumn column) {
    if (o == null) return null;
    Object result = object2ObjectImpl(o, column);
    return result != null ? result : object2ObjectImpl(String.valueOf(o), column);
  }

  private @Nullable Object object2ObjectImpl(@NotNull Object o, GridColumn column) {
    Converter<Object, Object> converter = myToObject.get(getClass(o));
    return converter != null ? converter.convert(o, column) : null;
  }

  protected @NotNull Class<?> getClass(@NotNull Object o) {
    return o.getClass();
  }

  protected <X> void put(@NotNull Class<X> aClass, Converter<Object, Object> value) {
    myToObject.put(aClass, value);
  }

  protected <X> void register(@NotNull Class<X> aClass, Converter<X, Object> value) {
    myToObject.register(aClass, value);
  }

  protected interface Converter<X, V> {
    V convert(X o, GridColumn column);
  }

  private static final class MyMap<T> extends ClassMap<Converter<Object, T>> {
    private MyMap() {
      super(new ConcurrentHashMap<>());
    }

    public <X> void register(@NotNull Class<X> aClass, Converter<X, T> value) {
      //noinspection unchecked
      super.put(aClass, (Converter<Object, T>)value);
    }
  }

  private void addToBoxedArrayConverters() {
    register(boolean[].class, new Converter<>() {
      @Override
      public Object convert(boolean[] o, GridColumn column) {
        Object[] boxed = new Object[o.length];
        for (int i = 0; i < o.length; i++) {
          boxed[i] = o[i];
        }
        return boxed;
      }
    });
    register(byte[].class, new Converter<>() {
      @Override
      public Object convert(byte[] o, GridColumn column) {
        Object[] boxed = new Object[o.length];
        for (int i = 0; i < o.length; i++) {
          boxed[i] = o[i];
        }
        return boxed;
      }
    });
    register(char[].class, new Converter<>() {
      @Override
      public Object convert(char[] o, GridColumn column) {
        Object[] boxed = new Object[o.length];
        for (int i = 0; i < o.length; i++) {
          boxed[i] = o[i];
        }
        return boxed;
      }
    });
    register(double[].class, new Converter<>() {
      @Override
      public Object convert(double[] o, GridColumn column) {
        return Arrays.stream(o).boxed().toArray();
      }
    });
    register(float[].class, new Converter<>() {
      @Override
      public Object convert(float[] o, GridColumn column) {
        Object[] boxed = new Object[o.length];
        for (int i = 0; i < o.length; i++) {
          boxed[i] = o[i];
        }
        return boxed;
      }
    });
    register(int[].class, new Converter<>() {
      @Override
      public Object convert(int[] o, GridColumn column) {
        return Arrays.stream(o).boxed().toArray();
      }
    });
    register(long[].class, new Converter<>() {
      @Override
      public Object convert(long[] o, GridColumn column) {
        return Arrays.stream(o).boxed().toArray();
      }
    });
    register(short[].class, new Converter<>() {
      @Override
      public Object convert(short[] o, GridColumn column) {
        Object[] boxed = new Object[o.length];
        for (int i = 0; i < o.length; i++) {
          boxed[i] = o[i];
        }
        return boxed;
      }
    });
  }
}
