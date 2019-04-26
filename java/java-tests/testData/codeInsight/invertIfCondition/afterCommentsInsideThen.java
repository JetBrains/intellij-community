// "Invert 'if' condition" "true"
public class C {
   public boolean isAcceptable(Object element) {
       if (element == null) {
           return false;
       }
       System.out.println();
       //c1
       if (true) {
         //c2
       }
       return false;
    }
}
