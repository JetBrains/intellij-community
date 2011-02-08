import java.util.*;


class SortedList<E extends Comparable> extends ArrayList<E>
{
  public static <T> T binarySearch(List<? extends Comparable<? super T>> list, T key) {
    return null;
  }

  public boolean add(E e){
      <ref>binarySearch(this,e); //infer E
  }
}