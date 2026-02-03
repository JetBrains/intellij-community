// "Replace with <>" "false"
class Test {
    class DiamondTest<T> {
        DiamondTest<String> s = new DiamondTest<St<caret>ring>(){
        }; // anonymous inner class
    }
}
