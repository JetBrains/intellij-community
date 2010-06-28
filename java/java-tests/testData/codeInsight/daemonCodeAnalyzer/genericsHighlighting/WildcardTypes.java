import java.util.*;

class a {
    public void printList(List<?> list) {
        for (Iterator<?> i = list.iterator(); i.hasNext();) {
            System.out.println(i.next().toString());
        }
    }
}

class b<<warning descr="Type parameter 'T' is never used">T</warning>> {
    public interface Lst <E, Self extends Lst<E, Self>> {
        Self subList(int fromIndex, int toIndex);
    }

    public static Lst<?, ?> foo(Lst<?, ?> lst) {
       Lst<?, ?> myl = lst.subList(0, 2);
       return myl;
    }
}

class ThingUser <V> {
    V v;
    {
        new ThingUser<<error descr="Wildcard type '?' cannot be instantiated directly">?</error>>() {
        };
    }
}

class SuperWildcardTest {
    static void method(List<?> list) {
        <error descr="Incompatible types. Found: 'java.util.List<capture<?>>', required: 'java.util.List<? super java.lang.String>'">List<? super String>  l = list;</error>
        l.size();
    }
}

class IdeaDev4166 {
    Map<String, Object> f( Map<String, ?> fieldsTemplate) {
        return new HashMap<String, Object>( fieldsTemplate);
    }
}

//IDEADEV-5816
class TwoD {
  int x, y;
  TwoD(int a, int b) {
    x = a;
    y = b;
  }
}
// Three-dimensional coordinates.
class ThreeD extends TwoD {
  int z;
  ThreeD(int a, int b, int c) {
    super(a, b);
    z = c;
  }
}
// Four-dimensional coordinates.
class FourD extends ThreeD {
  int t;
  FourD(int a, int b, int c, int d) {
    super(a, b, c);
    t = d;
  }
}
// This class holds an array of coordinate objects.
class Coords<T extends TwoD> {
  T[] coords;
  Coords(T[] o) { coords = o; }
}

// Demonstrate a bounded wildcard.
class BoundedWildcard {

  static void showXY(Coords<? extends TwoD> c) {
    System.out.println("X Y Coordinates:");
    for(int i=0; i < c.coords.length; i++) {
      System.out.println(c.coords[i].x + " " + c.coords[i].y);
    }
    System.out.println();
  }

  static void showXYZ(Coords<? extends ThreeD> c) {
    System.out.println("X Y Z Coordinates:");
    for(int i=0; i < c.coords.length; i++)
      System.out.println(c.coords[i].x + " " +
                         c.coords[i].y + " " +
                         c.coords[i].z);
    System.out.println();
  }

  static void showAll(Coords<? extends FourD> c) {
    System.out.println("X Y Z T Coordinates:");
    for(int i=0; i < c.coords.length; i++)
      System.out.println(c.coords[i].x + " " +
                         c.coords[i].y + " " +
                         c.coords[i].z + " " +
                         c.coords[i].t);
    System.out.println();
  }

  public static void main(String args[]) {
    TwoD td[] = {
      new TwoD(0, 0),
      new TwoD(7, 9),
      new TwoD(18, 4),
      new TwoD(-1, -23)
    };
    Coords<TwoD> tdlocs = new Coords<TwoD>(td);
    System.out.println("Contents of tdlocs.");
    showXY(tdlocs); // OK, is a TwoD
    showXYZ<error descr="'showXYZ(Coords<? extends ThreeD>)' in 'BoundedWildcard' cannot be applied to '(Coords<TwoD>)'">(tdlocs)</error>;
    showAll<error descr="'showAll(Coords<? extends FourD>)' in 'BoundedWildcard' cannot be applied to '(Coords<TwoD>)'">(tdlocs)</error>;
    // Now, create some FourD objects.
    FourD fd[] = {
      new FourD(1, 2, 3, 4),
      new FourD(6, 8, 14, 8),
      new FourD(22, 9, 4, 9),
      new FourD(3, -2, -23, 17)
    };
    Coords<FourD> fdlocs = new Coords<FourD>(fd);
    System.out.println("Contents of fdlocs.");
    // These are all OK.
    showXY(fdlocs);
    showXYZ(fdlocs);
    showAll(fdlocs);
  }
}
//end of IDEADEV-5816

interface I33 {}
public class Q<T extends I33> {
    T t;
    <V extends I33> List<V> foo(Q<V> v) {
      v.hashCode();
      return null;
    }

    List<? extends I33>  g (Q<?> q) {
      return foo(q);
    }
}

//IDEADEV-16628 
class CollectionHelper {
  public static <A> Collection<A> convertDown(Collection<? super A> collection) {
    return collection == null ? null : null;
  }
  public static <A> Collection<A> convertUp(Collection<? extends A> collection) {
    return collection == null ? null : null;
  }

  public static void main(String[] args) {
    // Downcast examples
    final Collection<Number> numbers1 = new ArrayList<Number>(1);
    Collection<Integer> integers1 =  CollectionHelper.convertDown(numbers1);
    integers1.hashCode();
    // Upcast example
    final Collection<Integer> integers4 = new ArrayList<Integer>(1);
    final Collection<Number> numbers4 = CollectionHelper.<Number>convertUp(integers4);
    numbers4.hashCode();
  }
}