interface I1 {}
interface I2 extends I1{}
interface I3 extends I1 {}

class PP {
    void f(Object o) {
        if (o instanceof I1) {
            if (!(o instanceof I2) || ((I2)o).getClass() != null){}
        }
    }
}