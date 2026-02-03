// "Invert 'if' condition" "true"
public class C {
   public boolean isAcceptable(Object element) {
        i<caret>f (element != null) {
            System.out.println();
        }//c1
        return false;
    }
}
