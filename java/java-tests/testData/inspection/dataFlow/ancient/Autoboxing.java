class Auto {
    void f(int k) {
        Integer i = 0;
        Integer j=0;    
        if (<warning descr="Condition 'i==j' is always 'true'">i==j</warning>) {

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
        if (<warning descr="Condition 'big1==big2' is always 'false'">big1==big2</warning>) {

        }
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
        if (<warning descr="Condition 'c==c2' is always 'true'">c==c2</warning>) {

        }
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
            if (<warning descr="Condition 'i1 == i2' is always 'true'">i1 == i2</warning>) {

            }
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
        System.out.println("(r==r1) is "+(r==r1)); // should report??

        Double r2 = 10.0;
        Double r3 = 10.0;
        System.out.println("(r2==r3) is "+(<warning descr="Condition 'r2==r3' is always 'false' when reached">r2==r3</warning>));

        float fd = 10.0f;
        Float fr = fd;
        Float fr1 = fd;
        System.out.println("(r==r1) is "+(fr==fr1)); // should report??

        Float fr2 = 10.0f;
        Float fr3 = 10.0f;
        System.out.println("(r2==r3) is "+(<warning descr="Condition 'fr2==fr3' is always 'false' when reached">fr2==fr3</warning>));
    
    }
}