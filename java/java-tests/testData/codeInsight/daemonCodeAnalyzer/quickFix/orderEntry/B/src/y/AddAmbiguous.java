// "Add dependency on module 'A'" "true"
package y;

import x.InA;

public class AddAmbiguous {
    InA<caret> a;
}
