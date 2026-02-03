import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;


class TestIDEA
{

  public static class Test1<Type extends List & Serializable>
  {
    public void process(Serializable s)
    {
    }
    public void process(Type t)
    {
    }
  }

  public static class Test2 extends Test1<ArrayList>
  {
    public void process(Serializable s)
    {
      super.process(s);
    }

    public void process(ArrayList t)
    {
      super.process(t);   // this call is OK resolving to parameterized method in super
    }
  }

  public static void main(String[] args)
  {
    Test2 test=new Test2();
    ArrayList list=new ArrayList();
    test.process(list);
    test.process((Serializable)list);
  }
}

class Key<T> {
    Object add(T v) {
        return v;
    }
}

class WKey<W, T> extends Key<T> {

    W add(T v) {
        return null;
    }
}

class IBug {

    public static <W, T> void addItem(WKey<W, T> key, T v) {
        key.add(v); // --> demetra draw this in red, see attachment
    }
}

//IDEADEV-7698
abstract class Collator implements Comparator<Object> {
  public abstract int compare(String source, String target);

  public int compare(Object o1, Object o2) {
    return compare((String)o1, (String)o2);
  }

  public void foo(Collator c) {
    c.compare("foo", "bar");
  }
}
//end of //IDEADEV-7698