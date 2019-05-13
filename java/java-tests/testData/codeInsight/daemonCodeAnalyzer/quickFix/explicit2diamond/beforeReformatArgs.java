// "Replace with <>" "true"
class Test<T> {
    public Test(String s1, String s2) {}

    Test<String> s = new Test<Str<caret>ing>("s1",
                                      "s2");

}
