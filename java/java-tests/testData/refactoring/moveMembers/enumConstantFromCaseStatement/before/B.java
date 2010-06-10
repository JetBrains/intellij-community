public class B {
    public static final int ONE = 1;
    void foo(A i) {
        switch (i) {
            case ONE :
              break;
        }
    }
}

class Usage {
    void foo(A i) {
        switch (i) {
            case B.ONE :
              break;
        }
    }
}