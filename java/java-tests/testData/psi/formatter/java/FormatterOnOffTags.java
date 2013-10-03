import java.util.*;

public class CollectionTest {
  public static void main(String[] args) {
    int size;
    HashSet collection = new HashSet();
    // @formatter:off
    String str1 = "Yellow",
            str2 = "White",
             str3 = "Green",
              str4 = "Blue";
    // @formatter:on
    Iterator iterator;
    // @formatter:off
    collection.add(str1);
      collection.add(str2);
        collection.add(str3);
    // @formatter:on
    collection.add(str4);
    iterator = collection.iterator();
    while (iterator.hasNext()) {
      System.out.print(iterator.next() + " ");
    }
  }
}
