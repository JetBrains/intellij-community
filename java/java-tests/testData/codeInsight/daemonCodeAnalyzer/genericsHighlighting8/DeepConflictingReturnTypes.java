class W {
    Object f() {
        return 0;
    }
}

class WW extends W {
    String f() {
        return null;
    }
}

interface IQ {
    void f();
}


<error descr="'f()' in 'WW' clashes with 'f()' in 'IQ'; attempting to use incompatible return type">class WWW extends WW implements IQ</error> {

}

