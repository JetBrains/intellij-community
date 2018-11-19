class Auto {
    void f(int k) {
        Integer i = 0;
        Integer j=0;    
        if (i==j) {
            // We don't track now whether two boxed constants are boxed to the same object
        }
        if (<warning descr="Condition 'i.equals(j)' is always 'true'">i.equals(j)</warning>) {
            // But equality is tracked
        }
        Integer j2 = 1;
        if (<warning descr="Condition 'i==j2' is always 'false'">i==j2</warning>) {
            
        }
        if (<warning descr="Condition 'i==0' is always 'true'">i==0</warning>) {
            
        }
        if (<warning descr="Condition 'i==1' is always 'false'">i==1</warning>) {
            
        }

        //////////////////
        Integer big1 = 333;
        Integer big2=  333;
        if (<warning descr="Condition 'big1==333' is always 'true'">big1==333</warning>) {
        }
        if (<warning descr="Condition '333 == big2' is always 'true'">333 == big2</warning>) {
        }
        if (<warning descr="Condition '333==333' is always 'true'">333==333</warning>) {
        }
        if (big1==big2) { } // Not known: integer cache could be enlarged
        if (<warning descr="Condition 'big1.equals(big2)' is always 'true'">big1.equals(big2)</warning>) {}
        int prim = big1;
        if (<warning descr="Condition 'prim==big2' is always 'true'">prim==big2</warning>) {

        }

        if (<warning descr="Condition 'big1 == 0' is always 'false'">big1 == 0</warning>) {

        }
        if (<warning descr="Condition 'big1 == 332' is always 'false'">big1 == 332</warning>) {

        }

        Character c = 1234;
        if (<warning descr="Condition 'c==1234' is always 'true'">c==1234</warning>) {

        }
        if (<warning descr="Condition 'c==124' is always 'false'">c==124</warning>) {

        }
        c = 1;
        if (<warning descr="Condition 'c==1234' is always 'false'">c==1234</warning>) {

        }
        if (<warning descr="Condition 'c==c' is always 'true'">c==c</warning>) {

        }
        Character c2 = 1;
        if (c==c2) {
            // We don't track now whether two boxed constants are boxed to the same object
        }
        if (<warning descr="Condition 'c.equals(c2)' is always 'true'">c.equals(c2)</warning>) {}
    }
}
class aaa {
  int a;
  int b;
  void canBeStatic(int x) {
      for (int i=0;i<10;i++) {
       a = i;
      }

      a = 4;

    if (<warning descr="Condition 'a == 5' is always 'false'">a == 5</warning>) {
    }
  }


    void f(int p) {
        {
            int i = 1;
            Integer i1 = i;
            Integer i2 = i;
            if (i1 == i2) {

            }
            if (<warning descr="Condition 'i1.equals(i2)' is always 'true'">i1.equals(i2)</warning>) {}
        }
        {
            int i = p;
            Integer i1 = i;
            Integer i2 = i;
            if (i1 == i2) {

            }
        }
    }

}
class UsesDoubleAndFloat {
    void f() {
        double dd = 10.0;
        Double r = dd;
        Double r1 = dd;
        System.out.println("(r==r1) is "+(r==r1));
        System.out.println("(r.equals(r1)) is "+(<warning descr="Result of 'r.equals(r1)' is always 'true'">r.equals(r1)</warning>));

        Double r2 = 10.0;
        Double r3 = 10.0;
        System.out.println("(r2==r3) is "+(r2==r3));
        System.out.println("(r2.equals(r3)) is "+(<warning descr="Result of 'r2.equals(r3)' is always 'true'">r2.equals(r3)</warning>));

        float fd = 10.0f;
        Float fr = fd;
        Float fr1 = fd;
        System.out.println("(fr==fr1) is "+(fr==fr1));
        System.out.println("(fr.equals(fr1)) is "+(<warning descr="Result of 'fr.equals(fr1)' is always 'true'">fr.equals(fr1)</warning>));

        Float fr2 = 10.0f;
        Float fr3 = 10.0f;
        System.out.println("(fr2==fr3) is "+(fr2==fr3));
        System.out.println("(fr2.equals(fr3)) is "+(<warning descr="Result of 'fr2.equals(fr3)' is always 'true'">fr2.equals(fr3)</warning>));
    }
}