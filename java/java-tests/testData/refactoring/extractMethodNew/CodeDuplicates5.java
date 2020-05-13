class D {}
class C extends D {
    int myField = 10;
    int myOtherField = 10;

    {
        int i = 5;
        <selection>myField = i;
        myOtherField = i;</selection>

        C c = new C();

        c.myField = 12;
        c.myOtherField = 12;

        C c1 = new C();
        c1.myField = 12;
        myOtherField = 12;


        //not a duplicate: method is not visible with qualifer class
        D d1 = new D();
        d1.myField = 12;
        d1.myOtherField = 12;


        c.myField = 15;
        c1.myOtherField = 15;
    }
}