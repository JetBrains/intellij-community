
class CL {
    void f() {
        new Runnable() {

            public void run() {
                //To change body of implemented methods use File | Settings | File Templates.
            }
            public int hashCode() {
<caret>
                return super.hashCode();    //To change body of overridden methods use File | Settings | File Templates.
            }
        }.run();
    }    
}


