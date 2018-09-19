class A {

}

class B extends A {

}

public class Inst {
    public void x(Object a) {



        if (a instanceof B) {
            A aa =(A) a;
            if (<warning descr="Condition 'a instanceof A' is always 'true'">a instanceof A</warning>) {
                System.out.println("HeHe");
            }
            System.out.println(aa);
        }
    }

    public void x1() {
        Object a = new Object();
        if (<warning descr="Condition 'a instanceof B' is always 'false'">a instanceof B</warning>) {
            A aa =(A) a;
            if (a instanceof A) {
                System.out.println("HeHe");
            }
            System.out.println(aa);
        }
    }

    public void y(Object a) {
        if (a instanceof A) {}
        if (a instanceof B) {}
    }
}
