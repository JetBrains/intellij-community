import java.util.ArrayList;

class B {
    int method(ArrayList list) {
        A[] a = (A[])list.toArray(new A[0]);

        for(i = 0; i < a.length; i++) {
            A member = a[i];
        }
    }
}