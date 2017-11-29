public class Inner1 {
    private int a(){
        return 0;
    }

    private class A extends B{
        {
            int j = <ref>a();
        }
    }
}

class B {
    public int a(){
        return 0;
    }
}
