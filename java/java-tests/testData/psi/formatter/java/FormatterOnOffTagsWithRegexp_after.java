import java.util.*;

public class CollectionTest {
    public static void main(String[] args) {
        int size;
        HashSet collection = new HashSet();
        // The following fragement must not be formatteed
    String str1 = "Yellow",
            str2 = "White",
             str3 = "Green",
              str4 = "Blue";
    // end of fragment
        Iterator iterator;
        // do not format the following:
    collection.add(  str1  );
      collection.add(  str2  );
        collection.add(  str3  );
    // end of fragment
        collection.add(str4);
        iterator = collection.iterator();
        while (iterator.hasNext()) {
            System.out.print(iterator.next() + " ");
        }
    }
}
