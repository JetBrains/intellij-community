// "Apply conversion '.toArray(new T[0][])'" "false"

import java.util.*;
class TypeVarArray<T> {
  T[][] foo() {
    Set<T[]> set = new HashSet<>();
    T[][] arr = <caret>set;
    return arr;
  }
}