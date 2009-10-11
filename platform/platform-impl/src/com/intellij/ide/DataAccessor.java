/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Function;
import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.containers.ContainerUtil.skipNulls;

import java.lang.reflect.Array;
import java.util.List;

public abstract class DataAccessor<T> {

  public final T from(DataContext dataContext) {
    try {
      return getNotNull(dataContext);
    } catch(NoDataException e) {
      return null;
    }
  }

  protected abstract T getImpl(DataContext dataContext) throws NoDataException;

  public final T getNotNull(DataContext dataContext) throws NoDataException {
    T data = getImpl(dataContext);
    if (data == null) throw new NoDataException(toString());
    return data;
  }

  public static <T, Original> DataAccessor<T> createConvertor(final DataAccessor<Original> original,
                                                              final Function<Original, T> convertor) {
    return new DataAccessor<T>(){
      public T getImpl(DataContext dataContext) throws NoDataException {
        return convertor.fun(original.getNotNull(dataContext));
      }
    };
  }

  public static <T, Original> DataAccessor<T[]> createArrayConvertor(final DataAccessor<Original[]> original, final Function<Original, T> convertor, final Class<T> aClass) {
    return new DataAccessor<T[]>() {
      public T[] getImpl(DataContext dataContext) throws NoDataException {
        List<T> converted = skipNulls(map(original.getNotNull(dataContext), convertor));
        return converted.toArray((T[])Array.newInstance(aClass, converted.size()));
      }
    };
  }

  public static <T> DataAccessor<T> createConditionalAccessor(DataAccessor<T> accessor, Condition<T> condition) {
    return new ConditionalDataAccessor<T>(accessor, condition);
  }

  public static class SimpleDataAccessor<T> extends DataAccessor<T> {
    private final String myDataConstant;

    public SimpleDataAccessor(String dataConstant) {
      myDataConstant = dataConstant;
    }

    public T getImpl(DataContext dataContext) throws NoDataException {
      T data = (T)dataContext.getData(myDataConstant);
      if (data == null) throw new NoDataException(myDataConstant);
      return data;
    }
  }

  public static class SubClassDataAccessor<Super, Sub> extends DataAccessor<Sub> {
    private final DataAccessor<Super> myOriginal;
    private final Class<Sub> mySubClass;

    SubClassDataAccessor(DataAccessor<Super> original, Class<Sub> subClass) {
      myOriginal = original;
      mySubClass = subClass;
    }

    public Sub getImpl(DataContext dataContext) throws NoDataException {
      Object data = myOriginal.getNotNull(dataContext);
      if (!mySubClass.isInstance(data)) return null;
      return (Sub)data;
    }

    public static <Super, Sub extends Super> DataAccessor<Sub> create(DataAccessor<Super> accessor, Class<Sub> subClass) {
      return new SubClassDataAccessor<Super, Sub>(accessor, subClass);
    }
  }

  private static class ConditionalDataAccessor<T> extends DataAccessor<T> {
    private final DataAccessor<T> myOriginal;
    private final Condition<T> myCondition;

    public ConditionalDataAccessor(DataAccessor<T> original, Condition<T> condition) {
      myOriginal = original;
      myCondition = condition;
    }

    public T getImpl(DataContext dataContext) throws NoDataException {
      T value = myOriginal.getNotNull(dataContext);
      return myCondition.value(value) ? value : null;
    }
  }

  public static class NoDataException extends Exception {
    public NoDataException(String missingData) {
      super(IdeBundle.message("exception.missing.data", missingData));
    }
  }
}
