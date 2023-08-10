public class OverlyStrongTypeCastInspection{
    String test1(Bar bar){
        // Inspection says this cast can be weakened to MyIterator
        // when it can't and the quick fix breaks the code.
        return ((MyBar) bar).foos.string;
    }

    String test2(Object bar){
        // inspection does not suggest that (MyBar) can be weakened to (Bar)
        ((MyBar) bar).arg();
        // inspection does not suggest that (MyBar) can be weakened to (Bar)
        return ((MyBar) bar).string;
    }
}

interface Bar{
    String string = "bla";

    void arg();
}

class MyBar implements Bar{
    Foos foos;

    public void arg(){
    }
}

class Foos{
    public String string = "";

    public void foo(){
    }
}