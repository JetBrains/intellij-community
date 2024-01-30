// "Add dependency on module 'a'" "true"
package y;

import x.InA;

public class AddAmbiguous {
    InA<caret> a;
}
