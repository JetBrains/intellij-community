// "Move bound 'java.lang.Number' to the beginning of the bounds list of type parameter 'X'" "true"
class C <X extends Number & Runnable<caret>> {
    X x;
}
