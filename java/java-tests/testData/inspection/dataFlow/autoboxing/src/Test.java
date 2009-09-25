public class Auto {
    void f(int k) {
        Integer i = 0;
        Integer j=0;    
        if (i==j) {

        }
        Integer j2 = 1;
        if (i==j2) {
            
        }
        if (i==0) {
            
        }
        if (i==1) {
            
        }

        //////////////////
        Integer big1 = 333;
        Integer big2=  333;
        if (big1==333) {
        }
        if (333 == big2) {
        }
        if (333==333) {
        }
        if (big1==big2) {

        }
        int prim = big1;
        if (prim==big2) {

        }

        if (big1 == 0) {

        }
        if (big1 == 332) {

        }

        Character c = 1234;
        if (c==1234) {

        }
        if (c==124) {

        }
        c = 1;
        if (c==1234) {

        }
        if (c==c) {

        }
        Character c2 = 1;
        if (c==c2) {

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

    if (a == 5) {
    }
  }


    void f(int p) {
        {
            int i = 1;
            Integer i1 = i;
            Integer i2 = i;
            if (i1 == i2) {

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
        System.out.println("(r==r1) is "+(r==r1));

        Double r2 = 10.0;
        Double r3 = 10.0;
        System.out.println("(r2==r3) is "+(r2==r3));

        float fd = 10.0f;
        Float fr = fd;
        Float fr1 = fd;
        System.out.println("(r==r1) is "+(fr==fr1));

        Float fr2 = 10.0f;
        Float fr3 = 10.0f;
        System.out.println("(r2==r3) is "+(fr2==fr3));
    
    }
}