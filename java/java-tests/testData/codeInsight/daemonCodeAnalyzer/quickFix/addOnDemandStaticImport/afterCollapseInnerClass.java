// "Add on demand static import for 'java.lang.Thread'" "true"
package test;

import static java.lang.Thread.*;

public class Foo {
    {
        <caret>State en = new State();
    }
}
