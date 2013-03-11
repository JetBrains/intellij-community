public class Test {

        interface II {
            int _();
        }

        interface IL {
            long _();
        }

        void m0(II im) { }
        void m0(IL lm) { }

        {
            m0(() -> 1);
            m0<error descr="Ambiguous method call: both 'Test.m0(II)' and 'Test.m0(IL)' match">(null)</error>;
        }

        void m(II im, IL s) { }
        void m(IL lm, II s) { }

        {
            m<error descr="Ambiguous method call: both 'Test.m(II, IL)' and 'Test.m(IL, II)' match">(() -> 1,  () ->1)</error>;
        }


        void m1(II im, Object s) { }
        void m1(IL lm, Object s) { }

        {
            m1(() -> 1, null);
            m1(() -> 1, "");
        }

        void m2(II im1, II... im) { }
        void m2(IL... lm) { }


        void mi(int im1, int... im) { }
        void mi(long... lm) { }

         {
            mi (1);
            m2();
            m2<error descr="Ambiguous method call: both 'Test.m2(II, II...)' and 'Test.m2(IL...)' match">(()->1)</error>;
            m2(()->1, ()->1);
            m2(()->1, ()->1, ()->1);

            m2<error descr="Ambiguous method call: both 'Test.m2(II, II...)' and 'Test.m2(IL...)' match">(null, null, null)</error>;
            m2<error descr="Ambiguous method call: both 'Test.m2(II, II...)' and 'Test.m2(IL...)' match">(()->1, null, null)</error>;
            m2<error descr="Ambiguous method call: both 'Test.m2(II, II...)' and 'Test.m2(IL...)' match">(null, ()->1, null)</error>;
            m2<error descr="Ambiguous method call: both 'Test.m2(II, II...)' and 'Test.m2(IL...)' match">(null, null, ()->1)</error>;
            m2<error descr="Ambiguous method call: both 'Test.m2(II, II...)' and 'Test.m2(IL...)' match">(()->1, ()->1, null)</error>;
            m2<error descr="Ambiguous method call: both 'Test.m2(II, II...)' and 'Test.m2(IL...)' match">(null, ()->1, ()->1)</error>;
            m2<error descr="Ambiguous method call: both 'Test.m2(II, II...)' and 'Test.m2(IL...)' match">(()->1, null, ()->1)</error>;

            m2(()->1L, null, null);
            m2(null, ()->1L, null);
            m2(null, null, ()->1L);
            m2(()->1L, ()->1L, null);
            m2(null, ()->1L, ()->1L);
            m2(()->1L, null, ()->1L);
            m2(()->1L, ()->1L, ()->1L);
         }

        void m3(II... im) {}
        void m3(IL... lm) {}

        {
            m3<error descr="Ambiguous method call: both 'Test.m3(II...)' and 'Test.m3(IL...)' match">()</error>;
            m3(() -> 1);
            m3(() -> 1, () -> 1);
            m3(() -> 1, () -> 1, () -> 1);

            m3<error descr="Ambiguous method call: both 'Test.m3(II...)' and 'Test.m3(IL...)' match">(null, null)</error>;
            m3<error descr="Ambiguous method call: both 'Test.m3(II...)' and 'Test.m3(IL...)' match">(() -> 1,  null)</error>;
            m3<error descr="Ambiguous method call: both 'Test.m3(II...)' and 'Test.m3(IL...)' match">(null, () -> 1)</error>;
            m3(() -> 1L, null);
            m3(null, () -> 1L);
        }

}
