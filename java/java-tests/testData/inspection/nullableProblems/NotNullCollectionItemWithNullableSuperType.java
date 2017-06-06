import typeUse.*;
import java.util.*;

class MyList<T extends @NotNull Number> extends ArrayList<T> {}

class SubList extends MyList<<warning descr="Non-null type argument is expected">@Nullable Integer</warning>> {
  MyList<<warning descr="Non-null type argument is expected">@Nullable Integer</warning>> myList;
}



