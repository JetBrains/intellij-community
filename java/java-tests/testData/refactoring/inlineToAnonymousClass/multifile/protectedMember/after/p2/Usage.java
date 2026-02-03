package p2;

import p1.ParentWithProtected;

public class Usage {
    public void test() {
        ParentWithProtected sws = new ParentWithProtected() {
            public void creaseMe(int i) {
              increaseMe(i);
            }
        };
    }
}