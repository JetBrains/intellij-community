class C {
    void printName(String firstName,  String lastName) {
        System.out.println( "My first name is " <caret>+ //comment1
                                                         firstName //comment2
                                                       + " and my last name is " + lastName );
    }
}