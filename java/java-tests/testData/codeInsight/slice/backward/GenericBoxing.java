public class Boxi {
    private interface Foo<T> {
            T t();
        }

         {
            Foo<String> a = new Foo<String>() {
                public String t() {
                    return "a";
                }
            };
            Foo<Integer> b = new Foo<Integer>() {
                public Integer t() {
                    return <flown11>42;
                }
            };

            int <caret>i = <flown1>b.t();
        }

}