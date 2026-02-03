import java.math.BigInteger;
import java.util.function.Function;

class Test {
    interface X {
        void methodFromX();
        void methodFromX2();
    }

    interface Y {
        void methodFromY();
        void methodFromY2();
    }

    interface Z extends X {}

    void test(Object obj) {
        if(obj instanceof Z && Math.random() > 0.5) {
            return;
        }
        if(obj instanceof X && obj instanceof Y) {
            obj.method<caret>
        }
    }
}