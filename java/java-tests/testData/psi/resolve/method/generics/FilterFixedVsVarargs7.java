import java.util.List;

 class A {
     static <T> List<T[]> listOf(T[]... elements) {
         System.out.println("in multiple");
         return Arrays.asList(elements);
     }

     static <T> List<T[]> listOf(T[] elements) {
         System.out.println("in single");
         return Collections.singletonList(elements);
     }

     public static void main(String[] args) {
         String[] array = {"foo", "bar"};
         //resolves to nonvarargs method
         List<String[]> listOfString =  <ref>listOf(array);
     }
 }
