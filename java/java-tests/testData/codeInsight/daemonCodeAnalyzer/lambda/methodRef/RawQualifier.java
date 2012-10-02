class MyTest {
    
    static class Foo<T> {
        T m() { 
          return null; 
        }
    }
    
    interface I {
        Integer m(Foo<Integer> f);
    }

    public static void main(String[] args) {
        I i = Foo::m;
    }
}