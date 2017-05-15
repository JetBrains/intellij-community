import typeUse.*;
import java.util.*;

class MyList<T extends @NotNull Number> extends ArrayList<T> {}

class SubList extends <warning descr="Nullable type arguments where non-null ones are expected">MyList</warning><@Nullable Integer> {
  <warning descr="Nullable type arguments where non-null ones are expected">MyList</warning><@Nullable Integer> myList;
}