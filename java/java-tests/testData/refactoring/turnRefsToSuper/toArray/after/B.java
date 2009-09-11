import java.util.ArrayList;

class B {
    int method(ArrayList list) {
        I[] a = (I[])list.toArray(new I[0]);

        for(i = 0; i < a.length; i++) {
            I member = a[i];
        }
    }
}