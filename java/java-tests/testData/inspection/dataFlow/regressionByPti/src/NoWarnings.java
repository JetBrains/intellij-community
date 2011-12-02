public class AAA {
 public static void main(String[] args) {

   for (int i = 0; i < args.length; i++) {
     final String element = args[i];

     if (i == 1) {
       if (element != null) { // !!!!! WRONG: element[1] == null
         return;
       }
     }
     else if (element == null) {
       return;
     }
   }
 }
}
