package p;

import java.util.AbstractList;
import java.util.Collection;

public abstract class List7 extends AbstractList<String> {
  @Override
  public boolean addAll(Collection<? extends String> c) {
    return true;
  }
}