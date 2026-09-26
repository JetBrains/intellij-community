// "Generate overloaded method with default parameter values" "true"
class Test {
    static void foo() {
        foo(0);
    }

    static native void foo(int ii) {}
  // native method intentionally has body
}