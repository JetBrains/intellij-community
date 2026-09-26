// "Merge with 'case Integer _'" "true"
class X {
    void foo(Object o) {
        switch (o) {
            case Integer _, StringBuffer _ when o.hashCode()==1:
            case String _:
                System.out.println("hello");
                break;
            default:
                System.out.println("hello2");
                break;
        }
    }
}