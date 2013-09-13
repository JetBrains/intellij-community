package com.intellij.compilerOutputIndex.api.descriptor;

import com.intellij.util.io.DataExternalizer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class ArrayListDataExternalizer<E> implements DataExternalizer<List<E>> {
  private final DataExternalizer<E> myDataExternalizer;

  public ArrayListDataExternalizer(final DataExternalizer<E> dataExternalizer) {
    myDataExternalizer = dataExternalizer;
  }

  @Override
  public void save(final DataOutput out, final List<E> list) throws IOException {
    out.writeInt(list.size());
    for (final E element : list) {
      myDataExternalizer.save(out, element);
    }
  }

  @Override
  public ArrayList<E> read(final DataInput in) throws IOException {
    final int size = in.readInt();
    final ArrayList<E> list = new ArrayList<E>(size);
    for (int i = 0; i < size; i++) {
      list.add(myDataExternalizer.read(in));
    }
    return list;
  }
}
