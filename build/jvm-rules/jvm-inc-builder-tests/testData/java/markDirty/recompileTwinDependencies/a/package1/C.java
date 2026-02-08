package package1;

import com.B;

public class C {
    {
        new B().get(); // resolves to invoking of "com/B.get:()Lpackage1/A;"
    }
}
