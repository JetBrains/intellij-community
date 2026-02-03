package p2;

import p.Test;

public class SubTest extends Test {
void foo() {
System.out.println(strings.length);
// "Cannot access 'length' in '_Dummy_.__Array__'" warning here
}
}
