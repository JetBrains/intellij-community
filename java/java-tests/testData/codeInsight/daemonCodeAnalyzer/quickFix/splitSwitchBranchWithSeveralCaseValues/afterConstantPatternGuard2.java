// "Split values of 'switch' branch" "true-preview"
class C {
    void foo(Object o) {
        switch (o) {
            case Integer _ when o.hashCode() > 0:
              System.out.println("hello");
              break;
            case String _ when o.hashCode() > 0:
                System.out.println("hello");
                break;
        }
    }
}
